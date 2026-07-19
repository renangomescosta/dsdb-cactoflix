import React, { useEffect, useRef, useState } from 'react'
import './recommendations.css'
import MovieCard from './MovieCard'

const recommendationsEndpoint = '/api/recommendations'
const moviesEndpoint = '/api/movies'
const tmdbApiKey = "e32a304ca6fe99a617c35c56571fef48"
const tmdbSearchEndpoint = 'https://api.themoviedb.org/3/search/movie'
const tmdbImageBaseUrl = 'https://image.tmdb.org/t/p/w500'

/**
 * Busca o poster de um filme na TMDB pelo nome.
 */
const fetchPosterByName = async (movieName) => {
    try {
        const cleanName = movieName.replace(/\s*\(\d{4}\)\s*$/, '').trim()
        const response = await fetch(
            `${tmdbSearchEndpoint}?api_key=${tmdbApiKey}&language=pt-BR&query=${encodeURIComponent(cleanName)}`
        )
        if (!response.ok) return null
        const data = await response.json()
        const result = data.results && data.results.length > 0 ? data.results[0] : null
        if (!result || !result.poster_path) return null
        return `${tmdbImageBaseUrl}${result.poster_path}`
    } catch {
        return null
    }
}

/**
 * Busca filmes do catálogo do backend (IDs compatíveis com o modelo SVD)
 * e depois busca os posters da TMDB pelo nome do filme.
 */
const getRandomMovies = async () => {
    const response = await fetch(moviesEndpoint, {
        headers: { 'X-Client-Email': 'test@dsid.com' },
    })

    if (!response.ok) {
        throw new Error('Falha ao buscar filmes do catálogo')
    }

    const allMovies = await response.json()

    // Embaralha e pega 10 aleatórios (Fisher-Yates)
    const shuffled = [...allMovies]
    for (let i = shuffled.length - 1; i > 0; i--) {
        const j = Math.floor(Math.random() * (i + 1))
        ;[shuffled[i], shuffled[j]] = [shuffled[j], shuffled[i]]
    }

    const selected = shuffled.slice(0, 10)

    // Busca posters em paralelo
    const moviesWithPosters = await Promise.all(
        selected.map(async (movie) => {
            const poster = await fetchPosterByName(movie.name)
            return {
                id: movie.id,
                title: movie.name,
                image: poster || 'https://via.placeholder.com/500x750?text=Sem+Imagem',
                genres: movie.genres || [],
                rating: 0,
            }
        })
    )

    return moviesWithPosters
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
        const ratedMovies = movies.filter((movie) => movie.rating > 0)

        if (ratedMovies.length < 3) {
            setError('Avalie pelo menos 3 filmes para obter recomendações personalizadas.')
            return
        }

        try {
            setSubmitting(true)
            setError('')

            const payload = {
                ratings: ratedMovies.map((movie) => ({
                    userId: 0,
                    movieId: movie.id,
                    rating: movie.rating,
                })),
            }

            const response = await fetch(`${recommendationsEndpoint}?k=10`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'X-Client-Email': 'test@dsid.com',
                },
                body: JSON.stringify(payload),
            })

            if (!response.ok) {
                const errorText = await response.text()
                console.error('Backend response:', response.status, errorText)
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
        return <div className='recommendations'>Carregando filmes do catálogo...</div>
    }

    if (error && movies.length === 0) {
        return <div className='recommendations'>{error}</div>
    }

    return (
        <div className='recommendations'>
            <h2 className='recommendations__title'>Avalie os filmes abaixo</h2>
            <p className='recommendations__subtitle'>Avalie pelo menos 3 filmes (mova o slider)</p>
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
