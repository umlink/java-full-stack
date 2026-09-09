import { RouterProvider } from "react-router-dom"

import { appRouter } from "@/router"

export function App() {
  return <RouterProvider router={appRouter} />
}

export default App
