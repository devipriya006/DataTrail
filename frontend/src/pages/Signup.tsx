import { useState } from "react";
import { useNavigate } from "react-router-dom";
import "../styles/global.css";
import "../styles/auth.css";

function Signup() {
  const navigate = useNavigate();

  const [fullName, setFullName] = useState("");
  const [email, setEmail] = useState("");
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
      const username = email.split("@")[0];

      const response = await fetch(
        "http://localhost:8080/auth/register",
        {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
          },
          body: JSON.stringify({
            username,
            email,
            password,
            roleId: 1
          }),
        }
      );

      if (!response.ok) {
        const message = await response.text();

        throw new Error(
          message || "Registration failed"
        );
      }

      alert("Account created successfully!");

      navigate("/login");

    } catch (error) {

      setError(
        error instanceof Error
          ? error.message
          : "Registration failed"
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
              CREATE YOUR WORKSPACE
            </div>

            <h1>
              Start tracking
              <span> every change.</span>
            </h1>

            <p>
              Create your TraceDB account and gain visibility into
              database changes, provenance, dependencies, and recovery.
            </p>

            <div className="feature-list">

              <div className="feature">
                <div className="feature-icon">⌁</div>

                <div>
                  <strong>Track every change</strong>
                  <small>
                    Keep a complete history of database activity.
                  </small>
                </div>
              </div>

              <div className="feature">
                <div className="feature-icon">◈</div>

                <div>
                  <strong>Understand dependencies</strong>
                  <small>
                    See how database changes relate to each other.
                  </small>
                </div>
              </div>

              <div className="feature">
                <div className="feature-icon">↶</div>

                <div>
                  <strong>Recover safely</strong>
                  <small>
                    Restore previous states without losing history.
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
                CREATE ACCOUNT
              </div>

              <h2>Create your account</h2>

              <p>
                Set up your TraceDB workspace.
              </p>

            </div>

            <form onSubmit={handleSubmit}>

              <div className="input-group">

                <label htmlFor="name">
                  Full name
                </label>

                <div className="input-wrapper">

                  <span className="input-icon">◎</span>

                  <input
                    id="name"
                    type="text"
                    placeholder="Your name"
                    value={fullName}
                    onChange={(event) =>
                      setFullName(event.target.value)
                    }
                    required
                  />

                </div>

              </div>

              <div className="input-group">

                <label htmlFor="email">
                  Email
                </label>

                <div className="input-wrapper">

                  <span className="input-icon">◎</span>

                  <input
                    id="email"
                    type="email"
                    placeholder="you@example.com"
                    value={email}
                    onChange={(event) =>
                      setEmail(event.target.value)
                    }
                    required
                  />

                </div>

              </div>

              <div className="input-group">

                <label htmlFor="password">
                  Password
                </label>

                <div className="input-wrapper">

                  <span className="input-icon">◆</span>

                  <input
                    id="password"
                    type="password"
                    placeholder="Create a password"
                    value={password}
                    onChange={(event) =>
                      setPassword(event.target.value)
                    }
                    required
                  />

                </div>

              </div>

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
                    ? "Creating account..."
                    : "Create TraceDB account"}
                </span>

                <span className="arrow">→</span>
              </button>

            </form>

            <div className="divider">
              <span>ALREADY HAVE AN ACCOUNT?</span>
            </div>

            <button
              className="create-account"
              onClick={() => navigate("/login")}
            >
              Back to sign in
              <span>→</span>
            </button>

            <div className="security-note">

              <span>⌁</span>

              <p>
                Your account will be protected by TraceDB's
                authentication and role-based access controls.
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

export default Signup;