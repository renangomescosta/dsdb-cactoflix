import React, { useState } from 'react';
import './App.css';
import Login from './Components/Login/Login';
import Recommendations from './Components/Login/recommendations';
import RecommendationsResult from './Components/Login/RecommendationsResult';

function App() {
  const [screen, setScreen] = useState('login');
  const [recommendations, setRecommendations] = useState([]);

  const handleSubmitComplete = (results) => {
    setRecommendations(results);
    setScreen('results');
  };

  const handleRetry = () => {
    setRecommendations([]);
    setScreen('recommendations');
  };

  return (
    <div>
      {screen === 'login' && (
        <Login onLogin={() => setScreen('recommendations')} />
      )}
      {screen === 'recommendations' && (
        <Recommendations onSubmitComplete={handleSubmitComplete} />
      )}
      {screen === 'results' && (
        <RecommendationsResult
          recommendations={recommendations}
          onRetry={handleRetry}
        />
      )}
    </div>
  );
}

export default App;
