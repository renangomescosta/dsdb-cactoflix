import React from 'react'
import './movieCard.css'

const MovieCard = ({ movie, onRatingChange }) => {
  const handleChange = (event) => {
    onRatingChange(movie.id, Number(event.target.value))
  }

  return (
    <div className='card'>
      <img src={movie.image} alt={movie.title} className='movie-image' />
      <h2>{movie.title}</h2>
      <p>Nota: {movie.rating}</p>

      <input
        type='range'
        min='0'
        max='10'
        step='0.5'
        value={movie.rating}
        onChange={handleChange}
      />
    </div>
  )
}

export default MovieCard