import React, { useEffect, useState } from 'react'
import './recommendations.css'
import MovieCard from './MovieCard'

const ratingsEndpoint = process.env.REACT_APP_RATINGS_ENDPOINT || '/api/ratings'
const tmdbApiKey = "e32a304ca6fe99a617c35c56571fef48"
const tmdbDiscoverEndpoint = 'https://api.themoviedb.org/3/discover/movie'
const tmdbGenresEndpoint = 'https://api.themoviedb.org/3/genre/movie/list'
const tmdbImageBaseUrl = 'https://image.tmdb.org/t/p/w500'
const maxRandomPage = 500

const buildRandomPage = () => Math.floor(Math.random() * maxRandomPage) + 1

const getRandomMovies = async () => {
    if (!tmdbApiKey) {
        throw new Error('REACT_APP_TMDB_API_KEY não definida')
    }

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

const Recommendations = () => {
    const [movies, setMovies] = useState([])
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState('')

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

    const handleSubmit = async () => {
        try {
            const payload = {
                movies: movies.map((movie) => ({
                    movieId: movie.id,
                    name: movie.title,
                    rating: movie.rating,
                    genres: movie.genres,
                })),
            }

            await fetch(ratingsEndpoint, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify(payload),
            })

            console.log('Notas enviadas com sucesso')


        } catch (error) {
            console.error('Erro ao enviar as notas:', error)
        }
    }

    if (loading) {
        return <div className='recommendations'>Carregando filmes aleatórios...</div>
    }

    if (error) {
        return <div className='recommendations'>{error}</div>
    }

    return (
        <div className='recommendations'>
            {movies.map((movie) => (
                <MovieCard
                    key={movie.id}
                    movie={movie}
                    onRatingChange={handleRatingChange}
                />
            ))}
            <div className='submit'>
                <button type='button' onClick={handleSubmit}>Submit</button>
            </div>
        </div>
    )
}

export default Recommendations