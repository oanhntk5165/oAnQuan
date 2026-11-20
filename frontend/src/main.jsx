import React from "react";
import { createRoot } from "react-dom/client";
import App from "./App.jsx";
import "./styles/theme.css";
import "./styles/lobby.css";
import "./styles/game.css";
import "./styles/pages.css";
import "./styles/responsive.css";
import "./styles/home.css";
import "./styles/community-chat.css";
import "./styles/auth.css";
import "./styles/profile.css";
import "./styles/play.css";
import "./styles/online.css";
import "./styles/ingame-dialogs.css";
import "./styles/results.css";
import "./styles/local-setup.css";

createRoot(document.getElementById("root")).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
);
