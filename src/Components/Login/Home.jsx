import React, { useEffect, useRef, useState } from 'react'
import { FaUserCircle } from 'react-icons/fa'
import MovieCard from './MovieCard'
import './home.css'

const moviesEndpoint = process.env.REACT_APP_MOVIES_ENDPOINT || '/movies'
const placeholderImage = 'https://via.placeholder.com/500x750?text=Filme'

const Home = ({ onRestart }) => {
    const [movies, setMovies] = useState([])
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState('')
    const carouselRef = useRef(null)
    const dragStateRef = useRef({
        isDragging: false,
        startX: 0,
        startScrollLeft: 0,
    })

    useEffect(() => {
        let isMounted = true

        const loadMovies = async () => {
            try {
                setLoading(true)
                setError('')

                const response = await fetch(moviesEndpoint)

                if (!response.ok) {
                    throw new Error('Falha ao buscar filmes do backend')
                }

                const movieList = await response.json()

                if (isMounted) {
                    setMovies(
                        (movieList || []).slice(0, 10).map((movie) => ({
                            id: movie.id,
                            title: movie.name,
                            image: placeholderImage,
                            rating: 0,
                            genres: movie.genres || [],
                        }))
                    )
                }
            } catch (requestError) {
                if (isMounted) {
                    setError(requestError.message || 'Erro ao carregar filmes')
                }
            } finally {
                if (isMounted) {
                    setLoading(false)
                }
            }
        }

        loadMovies()

        return () => {
            isMounted = false
        }
    }, [])

    const handleRatingChange = (movieId, newRating) => {
        setMovies((currentMovies) =>
            currentMovies.map((movie) =>
                movie.id === movieId ? { ...movie, rating: newRating } : movie
            )
        )
    }

    const handleCarouselPointerDown = (event) => {
        const carouselElement = carouselRef.current

        if (!carouselElement || event.target.closest('input, button, a, select, textarea')) {
            return
        }

        dragStateRef.current = {
            isDragging: true,
            startX: event.clientX,
            startScrollLeft: carouselElement.scrollLeft,
        }

        carouselElement.setPointerCapture(event.pointerId)
    }

    const handleCarouselPointerMove = (event) => {
        const carouselElement = carouselRef.current
        const dragState = dragStateRef.current

        if (!carouselElement || !dragState.isDragging) {
            return
        }

        const distance = event.clientX - dragState.startX
        carouselElement.scrollLeft = dragState.startScrollLeft - distance
    }

    const stopCarouselDrag = (event) => {
        const carouselElement = carouselRef.current

        dragStateRef.current.isDragging = false

        if (carouselElement && event?.pointerId !== undefined && carouselElement.hasPointerCapture(event.pointerId)) {
            carouselElement.releasePointerCapture(event.pointerId)
        }
    }

    return (
        <div className='home'>
            <div className='home__background' />
            <div className='home__brand'>
                <FaUserCircle className='home__brand-icon' />
            </div>

            <section className='home__hero'>
                <p className='home__eyebrow'>Assistir a seguir</p>
                <h1>Recomendações enviadas. Sua próxima fila começa aqui.</h1>
                <p className='home__description'>
                    Esta é a home final exibida após o submit. Os filmes continuam vindo do backend
                    em `GET /movies`. Hoje o endpoint retorna `id`, `name` e `genres`; a imagem
                    segue como placeholder até vocês enviarem o poster pelo serviço.
                </p>
                {onRestart && (
                    <div className='home__actions'>
                        <button type='button' onClick={onRestart}>Refazer avaliação</button>
                    </div>
                )}
            </section>

            <section className='home__carousel-section'>
                {loading && <p className='home__status'>Carregando filmes...</p>}
                {error && <p className='home__status'>{error}</p>}

                {!loading && !error && (
                    <div
                        className='home__carousel'
                        ref={carouselRef}
                        onPointerDown={handleCarouselPointerDown}
                        onPointerMove={handleCarouselPointerMove}
                        onPointerUp={stopCarouselDrag}
                        onPointerLeave={stopCarouselDrag}
                    >
                        {movies.map((movie) => (
                            <MovieCard
                                key={movie.id}
                                movie={movie}
                                onRatingChange={handleRatingChange}
                            />
                        ))}
                    </div>
                )}
            </section>
        </div>
    )
}

export default Home