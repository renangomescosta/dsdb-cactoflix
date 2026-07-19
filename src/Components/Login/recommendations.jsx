import React, { useEffect, useRef, useState } from 'react'
import './recommendations.css'
import MovieCard from './MovieCard'

const recommendationsEndpoint = '/api/recommendations'
const tmdbApiKey = "e32a304ca6fe99a617c35c56571fef48"
const tmdbDiscoverEndpoint = 'https://api.themoviedb.org/3/discover/movie'
const tmdbGenresEndpoint = 'https://api.themoviedb.org/3/genre/movie/list'
const tmdbImageBaseUrl = 'https://image.tmdb.org/t/p/w500'
const maxRandomPage = 500

const buildRandomPage = () => Math.floor(Math.random() * maxRandomPage) + 1

const getRandomMovies = async () => {
    const randomPage = buildRandomPage()
    const [moviesResponse, genresResponse] = await Promise.all([
        fetch(
            `${tmdbDiscoverEndpoint}?api_key=${tmdbApiKey}&language=pt-BR&sort_by=popularity.desc&page=${randomPage}`
        ),
        fetch(`${tmdbGenresEndpoint}?api_key=${tmdbApiKey}&language=pt-BR`),
    ])

    if (!moviesResponse.ok) {
        throw new Error('Falha ao buscar filmes da TMDB')
    }

    if (!genresResponse.ok) {
        throw new Error('Falha ao buscar gêneros da TMDB')
    }

    const moviesData = await moviesResponse.json()
    const genresData = await genresResponse.json()
    const genreMap = new Map(
        (genresData.genres || []).map((genre) => [genre.id, genre.name])
    )

    return (moviesData.results || []).slice(0, 10).map((movie) => ({
        id: movie.id,
        title: movie.title,
        image: movie.poster_path
            ? `${tmdbImageBaseUrl}${movie.poster_path}`
            : 'https://via.placeholder.com/500x750?text=Sem+Imagem',
        rating: 0,
        genres: (movie.genre_ids || [])
            .map((genreId) => genreMap.get(genreId))
            .filter(Boolean),
    }))
}

const Recommendations = ({ onSubmitComplete }) => {
    const [movies, setMovies] = useState([])
    const [loading, setLoading] = useState(true)
    const [submitting, setSubmitting] = useState(false)
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
                const randomMovies = await getRandomMovies()

                if (isMounted) {
                    setMovies(randomMovies)
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

    const handleSubmit = async () => {
        try {
            setSubmitting(true)
            setError('')


            const payload = {
                ratings: movies
                    .filter((movie) => movie.rating > 0)
                    .map((movie) => ({
                        userId: 0,
                        movieId: movie.id,
                        rating: Math.round(movie.rating),
                        name: movie.title,
                        genres: movie.genres,
                    })),
            }

            const response = await fetch(`${recommendationsEndpoint}?k=10&userId=0`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'X-Client-Email': 'test@dsid.com',
                },
                body: JSON.stringify(payload),
            })

            if (!response.ok) {
                throw new Error('Falha ao obter recomendações do backend')
            }

            const recommendations = await response.json()

            console.log('Recomendações recebidas:', recommendations)

            if (onSubmitComplete) {
                onSubmitComplete(recommendations)
            }
        } catch (submitError) {
            console.error('Erro ao enviar as notas:', submitError)
            setError(submitError.message || 'Erro ao enviar notas')
        } finally {
            setSubmitting(false)
        }
    }

    if (loading) {
        return <div className='recommendations'>Carregando filmes aleatórios...</div>
    }

    if (error && movies.length === 0) {
        return <div className='recommendations'>{error}</div>
    }

    return (
        <div className='recommendations'>
            <h2 className='recommendations__title'>Avalie os filmes abaixo</h2>
            {error && <p className='recommendations__error'>{error}</p>}
            <div
                className='carousel'
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
            <div className='submit'>
                <button type='button' onClick={handleSubmit} disabled={submitting}>
                    {submitting ? 'Enviando...' : 'Submit'}
                </button>
            </div>
        </div>
    )
}

export default Recommendations
