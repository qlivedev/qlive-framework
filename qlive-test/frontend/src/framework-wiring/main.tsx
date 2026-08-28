import React from "react";
import ReactDOM from "react-dom/client";
import { App } from "./App";

/**
 * Bootstrap only. Everything under framework-wiring/ must build and run
 * standalone as a "hello world" of the framework - it is what gets extracted
 * into the end-user template later. Never import from ../test-scenarios here.
 */
ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
);
