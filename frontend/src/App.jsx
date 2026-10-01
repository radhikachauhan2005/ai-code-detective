import { useState, useRef, useEffect } from 'react'
import DependencyGraph from './DependencyGraph'
import Investigation from './Investigation'

function App() {
  const [tab, setTab] = useState('chat')

  const [repoUrl, setRepoUrl] = useState('')
  const [analyzing, setAnalyzing] = useState(false)
  const [analyzeStatus, setAnalyzeStatus] = useState(null)
  const [chunkCount, setChunkCount] = useState(null)
  const [graph, setGraph] = useState(null)

  const [question, setQuestion] = useState('')
  const [messages, setMessages] = useState([])
  const [asking, setAsking] = useState(false)

  const chatEndRef = useRef(null)

  useEffect(() => {
    chatEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages, asking])

  const analyzeRepo = async () => {
    if (!repoUrl) return
    setAnalyzing(true)
    setAnalyzeStatus({ type: 'info', text: 'Cloning repository and generating embeddings…' })
    setChunkCount(null)
    setGraph(null)
    setMessages([])

    try {
      const res = await fetch(
        `http://localhost:8080/api/analyze?repoUrl=${encodeURIComponent(repoUrl)}`,
        { method: 'POST' }
      )
      const data = await res.json()
      if (data.status === 'success') {
        setChunkCount(data.chunksIndexed)
        setAnalyzeStatus({
          type: 'success',
          text: `Indexed ${data.chunksIndexed} chunks · ${data.graphNodes} nodes · ${data.graphEdges} edges`,
        })
        const gRes = await fetch('http://localhost:8080/api/graph')
        const gData = await gRes.json()
        if (gData.status === 'success') setGraph(gData.graph)
      } else {
        setAnalyzeStatus({ type: 'error', text: data.message })
      }
    } catch (err) {
      setAnalyzeStatus({ type: 'error', text: err.message })
    } finally {
      setAnalyzing(false)
    }
  }

  const askQuestion = async () => {
    if (!question || asking) return
    const q = question
    setQuestion('')
    setMessages(prev => [...prev, { role: 'user', text: q }])
    setAsking(true)
    try {
      const res = await fetch(`http://localhost:8080/api/chat?question=${encodeURIComponent(q)}`)
      const data = await res.json()
      setMessages(prev => [...prev, { role: 'ai', text: data.answer || '(no answer)' }])
    } catch (err) {
      setMessages(prev => [...prev, { role: 'ai', text: `Error: ${err.message}` }])
    } finally {
      setAsking(false)
    }
  }

  const repoName = repoUrl ? repoUrl.split('/').slice(-2).join('/') : null
  const ready = chunkCount != null

  return (
    <div className="min-h-screen flex flex-col bg-[#0d1117] text-[#e6edf3]">
      <header className="sticky top-0 z-10 border-b border-[#30363d] bg-[#161b22]/95 backdrop-blur-md">
        <div className="max-w-7xl mx-auto px-6 h-14 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="w-7 h-7 rounded-md bg-[#238636] flex items-center justify-center">
              <svg className="w-4 h-4 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2.5} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
              </svg>
            </div>
            <div className="flex items-baseline gap-2">
              <h1 className="text-sm font-semibold">Code Detective</h1>
              <span className="text-xs text-[#7d8590]">/ {repoName || 'no repo'}</span>
            </div>
          </div>
          {ready && (
            <div className="flex items-center gap-1.5 px-2.5 py-1 rounded-md bg-[#238636]/10 border border-[#238636]/40">
              <span className="w-1.5 h-1.5 rounded-full bg-[#3fb950]" />
              <span className="text-xs font-medium text-[#3fb950] mono">{chunkCount} chunks</span>
            </div>
          )}
        </div>

        <div className="max-w-7xl mx-auto px-6 flex gap-1 -mb-px">
          <TabButton active={tab === 'chat'} onClick={() => setTab('chat')}>
            Chat
          </TabButton>
          <TabButton active={tab === 'investigate'} onClick={() => setTab('investigate')}>
            🕵️ Investigate
          </TabButton>
          <TabButton active={tab === 'graph'} onClick={() => setTab('graph')}>
            Dependency Graph
          </TabButton>
        </div>
      </header>

      <main className="flex-1 max-w-7xl w-full mx-auto px-6 py-6">
        {tab === 'chat' && (
          <div className="grid grid-cols-1 lg:grid-cols-5 gap-6">
            <section className="lg:col-span-2 space-y-4">
              <div className="bg-[#161b22] rounded-lg border border-[#30363d]">
                <div className="px-4 py-3 border-b border-[#30363d]">
                  <h2 className="text-sm font-semibold">Repository</h2>
                  <p className="text-xs text-[#7d8590] mt-0.5">Paste a public GitHub URL</p>
                </div>
                <div className="p-4 space-y-3">
                  <input
                    type="text"
                    value={repoUrl}
                    onChange={(e) => setRepoUrl(e.target.value)}
                    placeholder="https://github.com/user/repo"
                    disabled={analyzing}
                    onKeyDown={(e) => e.key === 'Enter' && !analyzing && analyzeRepo()}
                    className="w-full px-3 py-2 text-sm mono rounded-md bg-[#0d1117] border border-[#30363d] text-[#e6edf3] placeholder-[#484f58] focus:border-[#1f6feb] focus:ring-2 focus:ring-[#1f6feb]/20 outline-none transition disabled:opacity-50"
                  />
                  <button
                    onClick={analyzeRepo}
                    disabled={analyzing || !repoUrl}
                    className="w-full py-2 rounded-md bg-[#238636] hover:bg-[#2ea043] text-white text-sm font-medium disabled:bg-[#21262d] disabled:text-[#484f58] disabled:cursor-not-allowed transition flex items-center justify-center gap-2"
                  >
                    {analyzing ? (<><Spinner /> Analyzing</>) : 'Analyze repository'}
                  </button>

                  {analyzeStatus && (
                    <div className={`text-xs px-3 py-2 rounded-md border flex items-start gap-2 mono ${
                      analyzeStatus.type === 'success' ? 'bg-[#238636]/10 border-[#238636]/40 text-[#3fb950]' :
                      analyzeStatus.type === 'error'   ? 'bg-[#f85149]/10 border-[#f85149]/40 text-[#f85149]' :
                                                         'bg-[#1f6feb]/10 border-[#1f6feb]/40 text-[#58a6ff]'
                    }`}>
                      {analyzeStatus.type === 'info' && <Spinner />}
                      <span>{analyzeStatus.text}</span>
                    </div>
                  )}
                </div>
              </div>
            </section>

            <section className="lg:col-span-3">
              <div className="bg-[#161b22] rounded-lg border border-[#30363d] h-[calc(100vh-9rem)] flex flex-col">
                <div className="px-4 py-3 border-b border-[#30363d]">
                  <h2 className="text-sm font-semibold">Chat</h2>
                </div>
                <div className="flex-1 overflow-y-auto p-4 space-y-4">
                  {messages.length === 0 && !asking && (
                    <div className="h-full flex flex-col items-center justify-center text-center px-6">
                      <p className="text-sm font-medium text-[#e6edf3]">No messages yet</p>
                      <p className="text-xs text-[#7d8590] mt-1">
                        {ready ? 'Ask a question about the codebase' : 'Analyze a repository first'}
                      </p>
                    </div>
                  )}
                  {messages.map((m, i) => (
                    <div key={i} className="flex gap-3">
                      <div className={`w-6 h-6 rounded-full flex items-center justify-center shrink-0 text-[10px] font-bold mono ${
                        m.role === 'user' ? 'bg-[#1f6feb] text-white' : 'bg-[#238636] text-white'
                      }`}>
                        {m.role === 'user' ? 'U' : 'AI'}
                      </div>
                      <div className="flex-1 min-w-0 pt-0.5">
                        <div className="text-[11px] font-semibold text-[#7d8590] mb-1">
                          {m.role === 'user' ? 'You' : 'Detective'}
                        </div>
                        <div className="text-sm text-[#e6edf3] whitespace-pre-wrap break-words leading-relaxed">
                          {m.text}
                        </div>
                      </div>
                    </div>
                  ))}
                  {asking && (
                    <div className="flex gap-3">
                      <div className="w-6 h-6 rounded-full bg-[#238636] flex items-center justify-center shrink-0 text-[10px] font-bold text-white mono">AI</div>
                      <div className="flex-1 pt-2">
                        <div className="flex gap-1">
                          <span className="w-1.5 h-1.5 bg-[#3fb950] rounded-full animate-bounce" style={{ animationDelay: '0ms' }} />
                          <span className="w-1.5 h-1.5 bg-[#3fb950] rounded-full animate-bounce" style={{ animationDelay: '150ms' }} />
                          <span className="w-1.5 h-1.5 bg-[#3fb950] rounded-full animate-bounce" style={{ animationDelay: '300ms' }} />
                        </div>
                      </div>
                    </div>
                  )}
                  <div ref={chatEndRef} />
                </div>
                <div className="p-3 border-t border-[#30363d]">
                  <div className="flex gap-2">
                    <input
                      type="text"
                      value={question}
                      onChange={(e) => setQuestion(e.target.value)}
                      placeholder={ready ? 'Ask about the code…' : 'Analyze a repo first'}
                      disabled={asking || !ready}
                      onKeyDown={(e) => e.key === 'Enter' && askQuestion()}
                      className="flex-1 px-3 py-2 text-sm rounded-md bg-[#0d1117] border border-[#30363d] text-[#e6edf3] placeholder-[#484f58] focus:border-[#1f6feb] focus:ring-2 focus:ring-[#1f6feb]/20 outline-none transition disabled:opacity-50"
                    />
                    <button
                      onClick={askQuestion}
                      disabled={asking || !question || !ready}
                      className="px-4 py-2 rounded-md bg-[#1f6feb] hover:bg-[#388bfd] text-white text-sm font-medium disabled:bg-[#21262d] disabled:text-[#484f58] disabled:cursor-not-allowed transition"
                    >
                      Send
                    </button>
                  </div>
                </div>
              </div>
            </section>
          </div>
        )}

        {tab === 'investigate' && <Investigation ready={ready} />}

        {tab === 'graph' && (
          <div className="bg-[#161b22] rounded-lg border border-[#30363d] h-[calc(100vh-9rem)] overflow-hidden">
            <DependencyGraph graph={graph} />
          </div>
        )}
      </main>
    </div>
  )
}

function TabButton({ active, onClick, children }) {
  return (
    <button
      onClick={onClick}
      className={`px-4 py-2 text-sm font-medium border-b-2 transition -mb-px ${
        active ? 'border-[#f78166] text-[#e6edf3]' : 'border-transparent text-[#7d8590] hover:text-[#e6edf3]'
      }`}
    >
      {children}
    </button>
  )
}

function Spinner() {
  return (
    <svg className="w-3.5 h-3.5 animate-spin" fill="none" viewBox="0 0 24 24">
      <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
      <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
    </svg>
  )
}

export default App