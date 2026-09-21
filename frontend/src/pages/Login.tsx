import { useState } from "react";
import { useNavigate } from "react-router-dom";
import "../styles/global.css";
import "../styles/auth.css";
import { saveSession } from "../auth";

type LoginResponse = {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  username: string;
  role: string;
};

function Login() {
  const navigate = useNavigate();

  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");

  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (
    event: React.FormEvent<HTMLFormElement>
  ) => {
    event.preventDefault();

    setError("");
    setLoading(true);

    try {
      const response = await fetch(
        "http://localhost:8080/auth/login",
        {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
          },
          body: JSON.stringify({
            username,
            password,
          }),
        }
      );

      if (!response.ok) {
        throw new Error("Invalid username or password");
      }

      const data: LoginResponse = await response.json();

      saveSession(data);

      navigate("/dashboard");
    } catch (error) {
      setError(
        error instanceof Error
          ? error.message
          : "Login failed"
      );
    } finally {
      setLoading(false);
    }
  };

  return (
    <main className="auth-page">
      <div className="background-grid" />

      <section className="auth-layout">

        {/* LEFT */}
        <div className="hero-section">

          <div className="logo">
            <span className="logo-mark">T</span>
            <span className="logo-text">TraceDB</span>
          </div>

          <div className="hero-content">

            <div className="eyebrow">
              <span className="status-dot" />
              DATABASE PROVENANCE PLATFORM
            </div>

            <h1>
              Every change has
              <span> a trace.</span>
            </h1>

            <p>
              Understand what changed, who changed it, and safely
              recover previous database states without losing history.
            </p>

            <div className="feature-list">

              <div className="feature">
                <div className="feature-icon">⌁</div>

                <div>
                  <strong>Complete provenance</strong>
                  <small>
                    Track database changes across time.
                  </small>
                </div>
              </div>

              <div className="feature">
                <div className="feature-icon">◈</div>

                <div>
                  <strong>Dependency intelligence</strong>
                  <small>
                    Understand relationships between changes.
                  </small>
                </div>
              </div>

              <div className="feature">
                <div className="feature-icon">↶</div>

                <div>
                  <strong>Safe recovery</strong>
                  <small>
                    Restore states without destroying history.
                  </small>
                </div>
              </div>

            </div>
          </div>

          <div className="hero-footer">
            <span>TRACE YOUR DATA</span>
            <span className="footer-line" />
            <span>PROTECT YOUR HISTORY</span>
          </div>

        </div>

        {/* RIGHT */}
        <div className="login-section">

          <div className="login-card">

            <div className="card-header">

              <div className="secure-badge">
                <span>◆</span>
                SECURE ACCESS
              </div>

              <h2>Welcome back</h2>

              <p>
                Sign in to continue to your TraceDB workspace.
              </p>

            </div>

            <form onSubmit={handleSubmit}>

              <div className="input-group">

                <label htmlFor="username">
                  Username
                </label>

                <div className="input-wrapper">

                  <span className="input-icon">◎</span>

                  <input
                    id="username"
                    type="text"
                    placeholder="you@example.com"
                    value={username}
                    onChange={(event) =>
                      setUsername(event.target.value)
                    }
                    required
                  />

                </div>

              </div>

              <div className="input-group">

                <div className="password-label">

                  <label htmlFor="password">
                    Password
                  </label>

                  <button type="button">
                    Forgot password?
                  </button>

                </div>

                <div className="input-wrapper">

                  <span className="input-icon">◆</span>

                  <input
                    id="password"
                    type="password"
                    placeholder="Enter your password"
                    value={password}
                    onChange={(event) =>
                      setPassword(event.target.value)
                    }
                    required
                  />

                  <button
                    type="button"
                    className="show-password"
                  >
                    ◉
                  </button>

                </div>

              </div>

              <label className="remember">

                <input type="checkbox" />

                <span>
                  Keep me signed in
                </span>

              </label>

              {error && (
                <div className="auth-error">
                  {error}
                </div>
              )}

              <button
                type="submit"
                className="sign-in-button"
                disabled={loading}
              >
                <span>
                  {loading
                    ? "Signing in..."
                    : "Sign in to TraceDB"}
                </span>

                <span className="arrow">→</span>
              </button>

            </form>

            <div className="divider">
              <span>NEW TO TRACEDB?</span>
            </div>

            <button
              className="create-account"
              onClick={() => navigate("/signup")}
            >
              Create an account
              <span>→</span>
            </button>

            <div className="security-note">

              <span>⌁</span>

              <p>
                Your database credentials and provenance data
                are protected with secure access controls.
              </p>

            </div>

          </div>

          <div className="login-footer">
            <span>© 2026 TraceDB</span>
            <span>Privacy</span>
            <span>Security</span>
          </div>

        </div>

      </section>
    </main>
  );
}

export default Login;