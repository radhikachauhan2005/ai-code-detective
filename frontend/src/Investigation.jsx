import { useState } from 'react'

export default function Investigation({ ready }) {
  const [bug, setBug] = useState('')
  const [loading, setLoading] = useState(false)
  const [report, setReport] = useState(null)
  const [error, setError] = useState(null)

  const investigate = async () => {
    if (!bug || loading) return
    setLoading(true)
    setReport(null)
    setError(null)

    try {
      const res = await fetch(
        `http://localhost:8080/api/investigate?bug=${encodeURIComponent(bug)}`,
        { method: 'POST' }
      )
      const data = await res.json()
      if (data.status === 'success' && data.report) {
        setReport(data.report)
      } else {
        setError(data.message || data.report?.error || 'Investigation failed')
      }
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="grid grid-cols-1 lg:grid-cols-5 gap-6">
      {/* Left: bug input */}
      <section className="lg:col-span-2 space-y-4">
        <div className="bg-[#161b22] rounded-lg border border-[#30363d]">
          <div className="px-4 py-3 border-b border-[#30363d]">
            <h2 className="text-sm font-semibold">Bug report</h2>
            <p className="text-xs text-[#7d8590] mt-0.5">Describe the bug — the detective will investigate</p>
          </div>
          <div className="p-4 space-y-3">
            <textarea
              value={bug}
              onChange={(e) => setBug(e.target.value)}
              placeholder="e.g. Checkout crashes when the cart is empty"
              rows={5}
              disabled={loading || !ready}
              onKeyDown={(e) => {
                if (e.key === 'Enter' && (e.metaKey || e.ctrlKey)) investigate()
              }}
              className="w-full px-3 py-2 text-sm rounded-md bg-[#0d1117] border border-[#30363d] text-[#e6edf3] placeholder-[#484f58] focus:border-[#1f6feb] focus:ring-2 focus:ring-[#1f6feb]/20 outline-none transition disabled:opacity-50 resize-none"
            />
            <button
              onClick={investigate}
              disabled={loading || !bug || !ready}
              className="w-full py-2 rounded-md bg-[#a371f7] hover:bg-[#b48ef8] text-white text-sm font-medium disabled:bg-[#21262d] disabled:text-[#484f58] disabled:cursor-not-allowed transition flex items-center justify-center gap-2"
            >
              {loading ? (<><Spin /> Investigating…</>) : 'Run investigation'}
            </button>
            <p className="text-[10px] text-[#484f58] text-center">Ctrl+Enter to run</p>

            {!ready && (
              <div className="text-xs px-3 py-2 rounded-md border bg-[#d29922]/10 border-[#d29922]/40 text-[#d29922] mono">
                Analyze a repository first (Chat tab)
              </div>
            )}

            {error && (
              <div className="text-xs px-3 py-2 rounded-md border bg-[#f85149]/10 border-[#f85149]/40 text-[#f85149] mono">
                {error}
              </div>
            )}
          </div>
        </div>

        <div className="bg-[#161b22] rounded-lg border border-[#30363d] p-4">
          <h3 className="text-[11px] font-semibold text-[#7d8590] uppercase tracking-wider mb-3">
            Try these
          </h3>
          <ul className="space-y-1.5 text-xs text-[#8b949e]">
            <li className="cursor-pointer hover:text-[#e6edf3]" onClick={() => setBug('Application fails to start with no clear error message')}>
              › App fails to start
            </li>
            <li className="cursor-pointer hover:text-[#e6edf3]" onClick={() => setBug('API endpoint returns 500 when input is empty')}>
              › Empty input crashes API
            </li>
            <li className="cursor-pointer hover:text-[#e6edf3]" onClick={() => setBug('Authentication token expires too early')}>
              › Token expires early
            </li>
          </ul>
        </div>
      </section>

      {/* Right: report */}
      <section className="lg:col-span-3">
        <div className="bg-[#161b22] rounded-lg border border-[#30363d] min-h-[calc(100vh-9rem)] flex flex-col">
          <div className="px-4 py-3 border-b border-[#30363d] flex items-center justify-between">
            <h2 className="text-sm font-semibold flex items-center gap-2">
              <span>🕵️</span> Investigation report
            </h2>
            {report && (
              <span className="text-[10px] mono text-[#7d8590]">
                {(report.durationMs / 1000).toFixed(1)}s
              </span>
            )}
          </div>

          <div className="flex-1 overflow-y-auto p-5 space-y-5">
            {loading && !report && (
              <div className="h-full flex flex-col items-center justify-center text-center">
                <div className="w-12 h-12 rounded-full bg-[#a371f7]/10 border border-[#a371f7]/40 flex items-center justify-center mb-3">
                  <Spin big />
                </div>
                <p className="text-sm font-medium text-[#e6edf3]">Running multi-phase investigation…</p>
                <p className="text-xs text-[#7d8590] mt-1">This takes 30–90 seconds with llama3.2</p>
                <p className="text-[10px] text-[#484f58] mono mt-4">
                  Retrieve → Trace chain → Analyze cause → Suggest fix → Impact
                </p>
              </div>
            )}

            {!report && !loading && (
              <div className="h-full flex flex-col items-center justify-center text-center px-6">
                <div className="w-14 h-14 rounded-full bg-[#21262d] flex items-center justify-center mb-4">
                  <span className="text-2xl opacity-40">🔍</span>
                </div>
                <p className="text-sm font-medium text-[#e6edf3]">No investigation yet</p>
                <p className="text-xs text-[#7d8590] mt-1 max-w-xs">
                  Describe a bug on the left and click Run investigation. You'll get a full report.
                </p>
              </div>
            )}

            {report && (
              <>
                {/* Header */}
                <Section icon="🎯" title="Bug" accent="#a371f7">
                  <p className="text-sm text-[#e6edf3] italic">"{report.bugDescription}"</p>
                </Section>

                {/* Relevant files */}
                {report.relevantFiles?.length > 0 && (
                  <Section icon="📍" title="Relevant files" accent="#3fb950">
                    <div className="space-y-1.5">
                      {report.relevantFiles.map((f, i) => (
                        <div key={i} className="text-xs mono text-[#8b949e] flex items-center gap-2">
                          <span className="w-1.5 h-1.5 rounded-full bg-[#3fb950] shrink-0" />
                          <span className="truncate">{f}</span>
                        </div>
                      ))}
                    </div>
                  </Section>
                )}

                {/* Call chain */}
                {report.callChain?.length > 0 && (
                  <Section icon="🔗" title="Call chain" accent="#1f6feb">
                    <pre className="text-[11px] mono text-[#8b949e] whitespace-pre overflow-x-auto leading-relaxed">
                      {report.callChain.join('\n')}
                    </pre>
                  </Section>
                )}

                {/* Root cause */}
                {report.probableRootCause && (
                  <Section
                    icon="🎯"
                    title="Probable root cause"
                    accent="#f85149"
                    badge={
                      <ConfidenceBadge level={report.confidence} />
                    }
                  >
                    <p className="text-sm text-[#e6edf3] whitespace-pre-wrap leading-relaxed">
                      {report.probableRootCause}
                    </p>
                    {report.evidence && (
                      <div className="mt-3 pt-3 border-t border-[#30363d]">
                        <div className="text-[10px] font-semibold text-[#7d8590] uppercase tracking-wider mb-2">
                          Evidence
                        </div>
                        <pre className="text-[11px] mono text-[#8b949e] whitespace-pre-wrap leading-relaxed">
                          {report.evidence}
                        </pre>
                      </div>
                    )}
                  </Section>
                )}

                {/* Reproduction */}
                {report.reproductionSteps?.length > 0 && (
                  <Section icon="🧪" title="Reproduction steps" accent="#d29922">
                    <ol className="space-y-2">
                      {report.reproductionSteps.map((s, i) => (
                        <li key={i} className="flex gap-3 text-sm text-[#e6edf3]">
                          <span className="shrink-0 w-5 h-5 rounded-full bg-[#d29922]/15 border border-[#d29922]/40 flex items-center justify-center text-[10px] mono text-[#d29922] font-bold">
                            {i + 1}
                          </span>
                          <span className="pt-0.5 leading-relaxed">{s}</span>
                        </li>
                      ))}
                    </ol>
                  </Section>
                )}

                {/* Suggested fix */}
                {report.suggestedFix && (
                  <Section
                    icon="🔧"
                    title="Suggested fix"
                    accent="#3fb950"
                    subtitle={report.suggestedFixFile}
                  >
                    <pre className="text-[11px] mono text-[#e6edf3] whitespace-pre-wrap overflow-x-auto bg-[#0d1117] border border-[#30363d] rounded-md p-3 leading-relaxed">
                      {report.suggestedFix}
                    </pre>
                  </Section>
                )}

                {/* Impact */}
                {report.impactedComponents?.length > 0 && (
                  <Section icon="⚠️" title="Impact analysis" accent="#f78166">
                    <ul className="space-y-1.5">
                      {report.impactedComponents.map((c, i) => (
                        <li key={i} className="text-xs text-[#e6edf3] flex items-start gap-2">
                          <span className="text-[#f78166] mt-0.5">▸</span>
                          <span className="leading-relaxed">{c}</span>
                        </li>
                      ))}
                    </ul>
                  </Section>
                )}
              </>
            )}
          </div>
        </div>
      </section>
    </div>
  )
}

/* ---------- helpers ---------- */

function Section({ icon, title, accent, badge, subtitle, children }) {
  return (
    <div
      className="rounded-lg border bg-[#0d1117] p-4"
      style={{ borderColor: '#30363d', borderLeft: `3px solid ${accent}` }}
    >
      <div className="flex items-center gap-2 mb-3">
        <span className="text-base">{icon}</span>
        <h3 className="text-xs font-semibold uppercase tracking-wider" style={{ color: accent }}>
          {title}
        </h3>
        {badge}
        {subtitle && (
          <span className="ml-auto text-[10px] mono text-[#7d8590] truncate max-w-[40%]">
            {subtitle}
          </span>
        )}
      </div>
      {children}
    </div>
  )
}

function ConfidenceBadge({ level }) {
  const colors = {
    high:   { bg: 'bg-[#3fb950]/15', border: 'border-[#3fb950]/40', text: 'text-[#3fb950]' },
    medium: { bg: 'bg-[#d29922]/15', border: 'border-[#d29922]/40', text: 'text-[#d29922]' },
    low:    { bg: 'bg-[#f85149]/15', border: 'border-[#f85149]/40', text: 'text-[#f85149]' },
  }
  const c = colors[level] || colors.medium
  return (
    <span className={`ml-auto text-[9px] font-bold px-2 py-0.5 rounded uppercase tracking-wider border mono ${c.bg} ${c.border} ${c.text}`}>
      {level} confidence
    </span>
  )
}

function Spin({ big }) {
  const size = big ? 'w-5 h-5' : 'w-3.5 h-3.5'
  return (
    <svg className={`${size} animate-spin`} fill="none" viewBox="0 0 24 24">
      <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
      <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
    </svg>
  )
}