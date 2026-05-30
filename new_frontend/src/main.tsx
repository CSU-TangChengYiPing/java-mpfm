import ReactDOM from "react-dom/client";
import "react-photo-view/dist/react-photo-view.css";
import { BrowserRouter } from "react-router-dom";
import App from "./App";
import "./i18n";
import { Provider } from "./provider";
import "./styles/globals.css";
import { createSessionAwareFetch } from "./utils/authSessionFetch";

const rawFetch = window.fetch.bind(window);

window.fetch = createSessionAwareFetch(rawFetch, () => {
  window.dispatchEvent(new Event("mpfm:unauthorized"));
});

ReactDOM.createRoot(document.getElementById("root")!).render(
  <BrowserRouter>
    <Provider>
      <App />
    </Provider>
  </BrowserRouter>
);
