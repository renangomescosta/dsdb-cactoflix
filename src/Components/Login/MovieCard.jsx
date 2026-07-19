import React, { useEffect, useRef, useState } from 'react'
import './movieCard.css'

const MovieCard = ({ movie, onRatingChange }) => {
  const titleRef = useRef(null)
  const [shouldScrollTitle, setShouldScrollTitle] = useState(false)

  const handleChange = (event) => {
    onRatingChange(movie.id, Number(event.target.value))
  }

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
  }, [movie.title])

  return (
    <div className='card'>
      <img src={movie.image} alt={movie.title} className='movie-image' draggable={false} />
      <h2 className={`movie-title${shouldScrollTitle ? ' movie-title--scroll' : ''}`} ref={titleRef}>
        <span>{movie.title}</span>
      </h2>
      <p>Nota: {movie.rating}</p>

      <input
        type='range'
        min='0'
        max='5'
        step='1'
        value={movie.rating}
        onChange={handleChange}
      />
    </div>
  )
}

export default MovieCard
