import React from 'react';
import './login.css'
import { FaUser } from "react-icons/fa";

const Login = ({ onLogin }) => {
    return (
        <div className='login-page'>
            <div className='wrapper'>
                <form onSubmit={(e) => e.preventDefault()}>
                    <div className='icon-conteiner'>
                        <FaUser className='icon' />
                    </div>

                    <h1>Login</h1>

                    <div className='input-box'>
                        <input type="text" placeholder='User' required />
                    </div>

                    <div className='input-box'>
                        <input type="password" placeholder='Password' required />
                    </div>

                    <button type='button' onClick={onLogin}>Login</button>

                </form>
            </div>
        </div>
    );
};

export default Login;;