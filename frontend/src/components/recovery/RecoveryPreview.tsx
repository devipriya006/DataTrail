import { useEffect, useRef, useState } from "react";
import "./RecoveryPreview.css";

type RecoveryPreviewData = {
  success: boolean;
  auditId: number;
  tableName: string;
  recordKey: string;
  currentState: Record<string, unknown>;
  targetState: Record<string, unknown>;
  changedFields: string[];
  allowed: boolean;
  overrideAllowed: boolean;
  status: string;
  blastRadiusEstimate: number;
};

type RecoveryApiResponse = {
  success?: boolean;
  message?: string;
  status?: string;
  recoveryId?: number;
  executionStatus?: string;
  dryRunResult?: string;
  auditEventId?: number;
};

type RecoveryStage =
  | "checking"
  | "ready"
  | "blocked"
  | "approving"
  | "executing"
  | "overriding"
  | "success"
  | "error";

type RecoveryPreviewProps = {
  auditId: number;
  accessToken: string;
  onClose: () => void;
  onUnauthorized: () => void;
  onCompleted: () => void;
  isAdmin: boolean;
};

const SENSITIVE_FIELDS = new Set([
  "password",
  "password_hash",
  "encrypted_password",
  "token",
  "access_token",
  "refresh_token",
  "secret",
]);

const visibleState = (
  state: Record<string, unknown> | undefined
): Record<string, unknown> =>
  Object.fromEntries(
    Object.entries(state ?? {}).filter(
      ([key]) => !SENSITIVE_FIELDS.has(key.toLowerCase())
    )
  );

const displayValue = (value: unknown) => {
  if (value === null || value === undefined) {
    return "(empty)";
  }

  if (typeof value === "object") {
    return JSON.stringify(value);
  }

  return String(value);
};

const responseMessage = (payload: RecoveryApiResponse) => {
  const message = payload.message ?? payload.dryRunResult;

  if (!message || /\b(select|update|delete|insert|password|credential)\b|stack trace/i.test(message)) {
    return "The recovery request could not be completed. Please try again.";
  }

  return message;
};

const EXECUTION_TIMEOUT_MS = 90_000;

const readableError = (cause: unknown, fallback: string) => {
  if (cause instanceof DOMException && cause.name === "AbortError") {
    return "The recovery request timed out before the server responded. Check the recovery status before retrying.";
  }

  if (!(cause instanceof Error) || /failed to fetch|unexpected token|json at position/i.test(cause.message)) {
    return fallback;
  }

  return cause.message;
};

function RecoveryPreview({
  auditId,
  accessToken,
  onClose,
  onUnauthorized,
  onCompleted,
  isAdmin,
}: RecoveryPreviewProps) {
  const [stage, setStage] = useState<RecoveryStage>("checking");
  const [preview, setPreview] = useState<RecoveryPreviewData | null>(null);
  const [error, setError] = useState("");
  const [overrideReason, setOverrideReason] = useState("");
  const [overrideConfirmation, setOverrideConfirmation] = useState("");
  const [successAuditEventId, setSuccessAuditEventId] = useState<number | null>(null);
  const executionStarted = useRef(false);

  const loadPreview = async (isActive: () => boolean = () => true) => {
    executionStarted.current = false;
    setStage("checking");
    setError("");

    try {
      const response = await fetch(
        `http://localhost:8080/recovery/audit/${auditId}/preview`,
        { headers: { Authorization: `Bearer ${accessToken}` } }
      );
      const payload = (await response.json()) as RecoveryPreviewData & RecoveryApiResponse;

      if (response.status === 401) {
        onUnauthorized();
        return;
      }

      if (!response.ok || payload.success !== true) {
        throw new Error(responseMessage(payload));
      }

      if (!isActive()) {
        return;
      }

      const safePreview = {
        ...payload,
        currentState: visibleState(payload.currentState),
        targetState: visibleState(payload.targetState),
      };
      setPreview(safePreview);
      setStage(safePreview.allowed ? "ready" : "blocked");
    } catch (cause) {
      if (isActive()) {
        setError(readableError(cause, "Unable to load the recovery preview. Please try again."));
        setStage("error");
      }
    }
  };

  useEffect(() => {
    let active = true;
    void loadPreview(() => active);

    return () => {
      active = false;
    };
  }, [auditId, accessToken]);

  const approveAndExecute = async () => {
    if (stage !== "ready" || !preview?.allowed || executionStarted.current) {
      return;
    }

    executionStarted.current = true;
    setStage("approving");
    setError("");
    const controller = new AbortController();
    const timeoutId = window.setTimeout(() => controller.abort(), EXECUTION_TIMEOUT_MS);

    try {
      const approvalResponse = await fetch(
        `http://localhost:8080/recovery/audit/${auditId}/approve`,
        {
          method: "POST",
          headers: { Authorization: `Bearer ${accessToken}` },
          signal: controller.signal,
        }
      );
      const approval = (await approvalResponse.json()) as RecoveryApiResponse;

      if (approvalResponse.status === 401) {
        onUnauthorized();
        return;
      }

      if (!approvalResponse.ok || approval.success !== true || approval.recoveryId == null) {
        throw new Error(responseMessage(approval));
      }

      setStage("executing");
      const executionResponse = await fetch(
        `http://localhost:8080/recovery/${approval.recoveryId}/execute`,
        {
          method: "POST",
          headers: { Authorization: `Bearer ${accessToken}` },
          signal: controller.signal,
        }
      );
      const execution = (await executionResponse.json()) as RecoveryApiResponse;

      if (executionResponse.status === 401) {
        onUnauthorized();
        return;
      }

      if (
        !executionResponse.ok ||
        execution.success !== true ||
        execution.executionStatus?.toUpperCase() !== "SUCCESS" ||
        execution.auditEventId == null
      ) {
        throw new Error(responseMessage(execution));
      }

      setSuccessAuditEventId(execution.auditEventId);
      setStage("success");
      onCompleted();
    } catch (cause) {
      setError(readableError(cause, "The recovery could not be completed. Please try again."));
      setStage("error");
    } finally {
      window.clearTimeout(timeoutId);
      if (controller.signal.aborted === false) {
        // leave executionStarted true after a finished attempt so Approve cannot double-submit
      }
    }
  };

  const restoreAnyway = async () => {
    if (!isAdmin || stage !== "blocked" || !preview?.overrideAllowed
        || !overrideReason.trim() || overrideConfirmation !== "RESTORE ANYWAY"
        || executionStarted.current) {
      return;
    }

    executionStarted.current = true;
    setStage("overriding");
    setError("");
    try {
      const response = await fetch(
        `http://localhost:8080/recovery/audit/${auditId}/restore-anyway`,
        {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            Authorization: `Bearer ${accessToken}`,
          },
          body: JSON.stringify({
            reason: overrideReason.trim(),
            confirmation: overrideConfirmation,
          }),
        }
      );
      const payload = (await response.json()) as RecoveryApiResponse;
      if (response.status === 401) {
        onUnauthorized();
        return;
      }
      if (!response.ok || payload.success !== true
          || payload.executionStatus?.toUpperCase() !== "SUCCESS"
          || payload.auditEventId == null) {
        throw new Error(responseMessage(payload));
      }
      setSuccessAuditEventId(payload.auditEventId);
      setStage("success");
      onCompleted();
    } catch (cause) {
      setError(readableError(cause, "The administrator override could not be completed."));
      setStage("error");
    }
  };

  const busy = stage === "checking" || stage === "approving" || stage === "executing" || stage === "overriding";

  return (
    <div className="recovery-overlay" role="presentation">
      <section
        className="recovery-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="recovery-title"
      >
        <header className="recovery-dialog-header">
          <div>
            <p className="section-label">AUDIT EVENT #{auditId}</p>
            <h2 id="recovery-title">
              {stage === "blocked" ? "Recovery blocked" : stage === "success" ? "Recovery completed" : "Recovery Preview"}
            </h2>
          </div>
          {!busy && stage !== "success" && (
            <button className="recovery-close" onClick={onClose} aria-label="Close recovery preview">
              ×
            </button>
          )}
        </header>

        {busy && (
          <p className="recovery-progress" role="status">
            {stage === "checking" && "Checking recovery..."}
            {stage === "approving" && "Approving recovery..."}
            {stage === "executing" && "Executing recovery..."}
            {stage === "overriding" && "Executing administrator override..."}
          </p>
        )}

        {stage === "blocked" && (
          <div className="recovery-blocked" role="alert">
            <strong>Recovery blocked</strong>
            <p>{preview?.status || "Normal recovery is blocked. DataTrail will not overwrite the current record without an eligible administrator override."}</p>
            {preview?.status && <small>Backend status: {preview.status}</small>}
          </div>
        )}

        {(stage === "ready" || stage === "approving" || stage === "executing" || stage === "overriding") && preview && (
          <div className="recovery-details">
            <dl className="recovery-summary">
              <div><dt>Table</dt><dd>{preview.tableName}</dd></div>
              <div><dt>Record</dt><dd>{preview.recordKey}</dd></div>
              <div><dt>Blast radius</dt><dd>{preview.blastRadiusEstimate} {preview.blastRadiusEstimate === 1 ? "record" : "records"}</dd></div>
              <div><dt>Safety</dt><dd className="recovery-ready-status">{preview.allowed ? "READY" : "BLOCKED"}</dd></div>
            </dl>

            <div className="recovery-state-columns">
              <StatePanel title="Current" state={preview.currentState} />
              <StatePanel title="Restore target" state={preview.targetState} />
            </div>

            <section className="recovery-changed-fields">
              <h3>Fields to restore</h3>
              {preview.changedFields.length > 0 ? (
                <ul>{preview.changedFields.map((field) => <li key={field}>{field}</li>)}</ul>
              ) : <p>No changed fields were reported.</p>}
            </section>
            {preview.status && <p className="recovery-backend-status">Backend status: {preview.status}</p>}
          </div>
        )}

        {stage === "success" && (
          <div className="recovery-success" role="status">
            <p>The previous state has been restored.</p>
            <p>The original audit event was preserved.</p>
            <p>PostgreSQL created and verified audit event #{successAuditEventId ?? "unknown"} in the restore transaction.</p>
          </div>
        )}

        {stage === "blocked" && isAdmin && preview?.overrideAllowed && (
          <section className="recovery-admin-override">
            <strong>Restore Anyway (Admin)</strong>
            <p>This bypasses only the detected record drift. The backend still verifies project access, table metadata, the live PostgreSQL trigger, and the audit hash chain.</p>
            <label>
              Administrator reason
              <textarea
                value={overrideReason}
                maxLength={2000}
                onChange={(event) => setOverrideReason(event.target.value)}
                disabled={busy}
              />
            </label>
            <label>
              Type RESTORE ANYWAY to confirm
              <input
                value={overrideConfirmation}
                onChange={(event) => setOverrideConfirmation(event.target.value)}
                disabled={busy}
                autoComplete="off"
              />
            </label>
          </section>
        )}

        {stage === "error" && (
          <div className="recovery-error" role="alert">
            <strong>Recovery request failed</strong>
            <p>{error}</p>
          </div>
        )}

        <footer className="recovery-dialog-actions">
          {stage === "blocked" ? (
            <>
              <button className="panel-link" onClick={onClose}>Close</button>
              {isAdmin && preview?.overrideAllowed && (
                <button
                  className="new-project-button"
                  onClick={() => void restoreAnyway()}
                  disabled={busy || !overrideReason.trim() || overrideConfirmation !== "RESTORE ANYWAY"}
                >
                  Restore Anyway (Admin)
                </button>
              )}
            </>
          ) : stage === "success" ? (
            <button className="new-project-button" onClick={onClose}>Back to Audit Logs</button>
          ) : stage === "error" ? (
            <>
              <button className="panel-link" onClick={onClose}>Close</button>
              <button className="new-project-button" onClick={() => void loadPreview()}>Retry recovery</button>
            </>
          ) : (
            <>
              <button className="panel-link" onClick={onClose} disabled={busy}>Cancel</button>
              <button
                className="new-project-button"
                onClick={() => void approveAndExecute()}
                disabled={stage !== "ready" || !preview?.allowed}
              >
                Approve &amp; Execute
              </button>
            </>
          )}
        </footer>
      </section>
    </div>
  );
}

function StatePanel({ title, state }: { title: string; state: Record<string, unknown> }) {
  const entries = Object.entries(state);

  return (
    <section className="recovery-state-panel">
      <h3>{title}</h3>
      {entries.length ? (
        <dl>
          {entries.map(([key, value]) => (
            <div key={key}><dt>{key}</dt><dd>{displayValue(value)}</dd></div>
          ))}
        </dl>
      ) : <p>No visible fields.</p>}
    </section>
  );
}

export default RecoveryPreview;