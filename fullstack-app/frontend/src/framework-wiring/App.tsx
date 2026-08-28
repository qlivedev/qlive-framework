import { useEffect, useState } from "react";
import { createFrameworkConfig, fetchGreeting } from "@qlive/frontend-lib";

const config = createFrameworkConfig();

export function App() {
  const [greeting, setGreeting] = useState("loading...");

  useEffect(() => {
    fetchGreeting(config)
      .then(setGreeting)
      .catch((err: Error) => setGreeting(`error: ${err.message}`));
  }, []);

  return (
    <main>
      <h1>qlive framework - hello world</h1>
      <p>{greeting}</p>
    </main>
  );
}
