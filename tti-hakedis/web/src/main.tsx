import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { App } from './App'
import './stil.css'

const kok = document.getElementById('kok')
if (!kok) throw new Error('#kok bulunamadı: index.html bozuk.')

createRoot(kok).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
