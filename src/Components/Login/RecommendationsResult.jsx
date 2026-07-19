import React, { useEffect, useRef, useState } from 'react'
import './recommendationsResult.css'

const tmdbApiKey = "e32a304ca6fe99a617c35c56571fef48"
const tmdbSearchEndpoint = 'https://api.themoviedb.org/3/search/movie'
const tmdbImageBaseUrl = 'https://image.tmdb.org/t/p/w500'

const fetchMovieDetails = async (movieName) => {
    try {
        // Clean the name: remove year in parentheses for better search results
        const cleanName = movieName.replace(/\s*\(\d{4}\)\s*$/, '').trim()

        const response = await fetch(
            `${tmdbSearchEndpoint}?api_key=${tmdbApiKey}&language=pt-BR&query=${encodeURIComponent(cleanName)}`
        )

        if (!response.ok) {
            return null
        }

        const data = await response.json()
        const result = data.results && data.results.length > 0 ? data.results[0] : null

        if (!result) {
            return null
        }

        return {
            image: result.poster_path
                ? `${tmdbImageBaseUrl}${result.poster_path}`
                : null,
            title: result.title || cleanName,
        }
    } catch {
        return null
    }
}

const RecommendationCard = ({ movie }) => {
    const titleRef = useRef(null)
    const [shouldScrollTitle, setShouldScrollTitle] = useState(false)

    useEffect(() => {
        const titleElement = titleRef.current

        if (!titleElement) {
            return undefined
        }

        const updateTitleOverflow = () => {
            setShouldScrollTitle(titleElement.scrollWidth > titleElement.clientWidth)
        }

        updateTitleOverflow()

        const resizeObserver = new ResizeObserver(updateTitleOverflow)
        resizeObserver.observe(titleElement)

        return () => {
            resizeObserver.disconnect()
        }
    }, [movie.displayTitle])

    return (
        <div className='result-card'>
            <img
                src={movie.image || 'https://via.placeholder.com/500x750?text=Sem+Imagem'}
                alt={movie.displayTitle}
                className='result-card__image'
                draggable={false}
            />
            <h2
                className={`result-card__title${shouldScrollTitle ? ' result-card__title--scroll' : ''}`}
                ref={titleRef}
            >
                <span>{movie.displayTitle}</span>
            </h2>
            {movie.score !== undefined && (
                <p className='result-card__score'>
                    Score: {movie.score.toFixed(2)}
                </p>
            )}
        </div>
    )
}

const RecommendationsResult = ({ recommendations, onRetry }) => {
    const [movies, setMovies] = useState([])
    const [loading, setLoading] = useState(true)
    const carouselRef = useRef(null)
    const dragStateRef = useRef({
        isDragging: false,
        startX: 0,
        startScrollLeft: 0,
    })

    useEffect(() => {
        let isMounted = true

        const loadMovieImages = async () => {
            setLoading(true)

            const moviesWithImages = await Promise.all(
                (recommendations || []).map(async (rec) => {
                    const details = await fetchMovieDetails(rec.name)

                    return {
                        movieId: rec.movieId,
                        name: rec.name,
                        score: rec.score,
                        displayTitle: details?.title || rec.name,
                        image: details?.image || null,
                    }
                })
            )

            if (isMounted) {
                setMovies(moviesWithImages)
                setLoading(false)
            }
        }

        loadMovieImages()

        return () => {
            isMounted = false
        }
    }, [recommendations])

    const handleCarouselPointerDown = (event) => {
        const carouselElement = carouselRef.current

        if (!carouselElement || event.target.closest('button, a')) {
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
        <div className='results'>
            <div className='results__background' />

            <section className='results__hero'>
                <h1 className='results__title'>Aqui estão as recomendações</h1>
                <p className='results__description'>
                    Com base nas suas avaliações, nosso modelo recomendou esses filmes para você!
                </p>
            </section>

            <section className='results__carousel-section'>
                {loading && <p className='results__status'>Buscando detalhes dos filmes...</p>}

                {!loading && (
                    <div
                        className='results__carousel'
                        ref={carouselRef}
                        onPointerDown={handleCarouselPointerDown}
                        onPointerMove={handleCarouselPointerMove}
                        onPointerUp={stopCarouselDrag}
                        onPointerLeave={stopCarouselDrag}
                    >
                        {movies.map((movie) => (
                            <RecommendationCard key={movie.movieId} movie={movie} />
                        ))}
                    </div>
                )}
            </section>

            <div className='results__actions'>
                <button type='button' onClick={onRetry}>
                    Tentar de novo com novos filmes
                </button>
            </div>
        </div>
    )
}

export default RecommendationsResult
