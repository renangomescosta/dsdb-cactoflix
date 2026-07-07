import React, { useState } from 'react';
import logo from './logo.svg';
import './App.css';
import Login from './Components/Login/Login';
import Recommendations from './Components/Login/recommendations';

function App() {
  const [screen, setScreen] = useState('login');

  return (
    <div>
      {screen === 'login' && <Login onLogin={() => setScreen('recommendations')} />}
      {screen === 'recommendations' && <Recommendations />}
    </div>
  );
}

export default App;
