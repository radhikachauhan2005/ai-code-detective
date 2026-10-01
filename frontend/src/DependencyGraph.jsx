// ============================================================
// DependencyGraph.jsx — v3 (2026-09-12)
// Professional React Flow view with sidebar, filters, details.
// If you see a "v3" comment at the top of the file in VS Code, this is the right file.
// ============================================================

import { useMemo, useState, useCallback, useEffect } from 'react'
import ReactFlow, {
  Background,
  Controls,
  MiniMap,
  Handle,
  Position,
  MarkerType,
  useNodesState,
  useEdgesState,
  useReactFlow,
  ReactFlowProvider,
} from 'reactflow'
import 'reactflow/dist/style.css'
import dagre from 'dagre'

const NODE_W = 210
const NODE_H_CLASS = 68
const NODE_H_METHOD = 40

/* ------- Node style config ------- */
const NODE_STYLE = {
  class:     { accent: '#3fb950', badge: 'bg-[#3fb950]/15 text-[#56d364]' },
  interface: { accent: '#1f6feb', badge: 'bg-[#1f6feb]/15 text-[#58a6ff]' },
  enum:      { accent: '#a371f7', badge: 'bg-[#a371f7]/15 text-[#c4b5fd]' },
  method:    { accent: '#1f6feb', badge: 'bg-[#1f6feb]/15 text-[#58a6ff]' },
  field:     { accent: '#7d8590', badge: 'bg-[#7d8590]/15 text-[#8b949e]' },
}

const EDGE_COLORS = {
  extends:      '#f78166',
  implements:   '#a371f7',
  imports:      '#484f58',
  calls:        '#3fb950',
  method_calls: '#3fb950',
  contains:     '#1f6feb',
  field_type:   '#7d8590',
  param_type:   '#d29922',
  return_type:  '#d29922',
}

/* ------- Custom node ------- */
function CustomNode({ data, selected }) {
  const style = NODE_STYLE[data.type] || NODE_STYLE.class
  const isSmall = data.type === 'method' || data.type === 'field'

  return (
    <div
      className={`rounded-lg bg-[#0d1117] shadow-lg transition-all ${
        selected ? 'ring-2 ring-[#58a6ff]' : ''
      }`}
      style={{
        width: NODE_W,
        border: `1px solid ${selected ? '#58a6ff' : '#30363d'}`,
        borderLeft: `3px solid ${style.accent}`,
      }}
    >
      <Handle type="target" position={Position.Top}
        style={{ background: '#484f58', width: 5, height: 5, border: 'none' }} />

      <div className={isSmall ? 'px-2.5 py-1.5' : 'px-3 py-2.5'}>
        <div className="flex items-center gap-2">
          {data.type === 'method' && (
            <span className="text-[#58a6ff] font-bold italic text-sm leading-none">ƒ</span>
          )}
          {data.type === 'field' && (
            <span className="text-[#7d8590] text-xs leading-none">◇</span>
          )}
          <span className={`truncate font-semibold text-[#e6edf3] ${isSmall ? 'text-xs' : 'text-[13px]'}`}>
            {data.label}
          </span>
          <span className={`ml-auto text-[9px] font-bold px-1.5 py-0.5 rounded uppercase tracking-wide ${style.badge}`}>
            {data.type}
          </span>
        </div>

        {!isSmall && (
          <>
            <div className="text-[10px] text-[#7d8590] mono truncate mt-1.5" title={data.filePath}>
              {data.filePath}
            </div>
            <div className="text-[9px] text-[#484f58] mono mt-0.5">
              {data.lineCount} lines
            </div>
          </>
        )}
      </div>

      <Handle type="source" position={Position.Bottom}
        style={{ background: '#484f58', width: 5, height: 5, border: 'none' }} />
    </div>
  )
}

const nodeTypes = { customNode: CustomNode }

/* ------- Dagre layout ------- */
function layoutGraph(nodes, edges) {
  const g = new dagre.graphlib.Graph()
  g.setDefaultEdgeLabel(() => ({}))
  g.setGraph({ rankdir: 'TB', nodesep: 40, ranksep: 90, marginx: 40, marginy: 40 })

  nodes.forEach((n) => {
    const small = n.data.type === 'method' || n.data.type === 'field'
    g.setNode(n.id, { width: NODE_W, height: small ? NODE_H_METHOD : NODE_H_CLASS })
  })
  edges.forEach((e) => g.setEdge(e.source, e.target))

  dagre.layout(g)

  return nodes.map((n) => {
    const pos = g.node(n.id)
    const small = n.data.type === 'method' || n.data.type === 'field'
    const h = small ? NODE_H_METHOD : NODE_H_CLASS
    return {
      ...n,
      position: { x: pos.x - NODE_W / 2, y: pos.y - h / 2 },
      targetPosition: 'top',
      sourcePosition: 'bottom',
    }
  })
}

/* ------- Inner graph (needs ReactFlowProvider) ------- */
function GraphInner({ graph }) {
  const [search, setSearch] = useState('')
  const [showFilters, setShowFilters] = useState({
    class: true, interface: true, enum: true, method: true, field: false,
  })
  const [edgeFilter, setEdgeFilter] = useState({
    extends: true, implements: true, imports: false, calls: true,
    method_calls: true, contains: true, field_type: true,
    param_type: false, return_type: false,
  })
  const [selectedNode, setSelectedNode] = useState(null)
  const { fitView } = useReactFlow()

  const { rfNodes, rfEdges } = useMemo(() => {
    if (!graph || !graph.nodes) return { rfNodes: [], rfEdges: [] }

    const filtered = graph.nodes.filter((n) => showFilters[n.type] !== false)
    const nodes = filtered.map((n) => ({
      id: n.id, type: 'customNode', data: n, position: { x: 0, y: 0 },
    }))

    const ids = new Set(nodes.map((n) => n.id))
    const edges = (graph.edges || [])
      .filter((e) => edgeFilter[e.type] !== false && ids.has(e.source) && ids.has(e.target))
      .map((e, i) => ({
        id: `e${i}`,
        source: e.source,
        target: e.target,
        type: 'smoothstep',
        animated: e.type === 'method_calls' || e.type === 'calls',
        style: { stroke: EDGE_COLORS[e.type] || '#484f58', strokeWidth: 1.4 },
        markerEnd: {
          type: MarkerType.ArrowClosed,
          color: EDGE_COLORS[e.type] || '#484f58',
          width: 14, height: 14,
        },
        data: { edgeType: e.type },
      }))

    return { rfNodes: layoutGraph(nodes, edges), rfEdges: edges }
  }, [graph, showFilters, edgeFilter])

  const [nodes, setNodes, onNodesChange] = useNodesState(rfNodes)
  const [edges, setEdges, onEdgesChange] = useEdgesState(rfEdges)

  useEffect(() => {
    setNodes(rfNodes)
    setEdges(rfEdges)
    setTimeout(() => fitView({ padding: 0.2, duration: 400 }), 50)
  }, [rfNodes, rfEdges, setNodes, setEdges, fitView])

  const styledNodes = useMemo(() => {
    if (!search) return nodes
    const q = search.toLowerCase()
    const matches = new Set(
      nodes.filter((n) => n.data.label.toLowerCase().includes(q)).map((n) => n.id)
    )
    return nodes.map((n) => ({
      ...n,
      style: {
        opacity: matches.has(n.id) ? 1 : 0.15,
        transition: 'opacity 0.2s',
      },
    }))
  }, [nodes, search])

  const onNodeClick = useCallback((_, node) => setSelectedNode(node.data), [])

  const classCount = nodes.filter(n => n.data.type === 'class').length
  const ifaceCount = nodes.filter(n => n.data.type === 'interface').length
  const enumCount  = nodes.filter(n => n.data.type === 'enum').length
  const methodCount = nodes.filter(n => n.data.type === 'method').length
  const fieldCount = nodes.filter(n => n.data.type === 'field').length

  return (
    <div className="h-full w-full flex">
      {/* LEFT SIDEBAR */}
      <aside className="w-60 border-r border-[#30363d] bg-[#0d1117] flex flex-col overflow-y-auto shrink-0">
        <div className="p-3 border-b border-[#30363d]">
          <div className="text-[10px] font-semibold text-[#7d8590] uppercase tracking-wider mb-2">
            Search
          </div>
          <input
            type="text"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Filter nodes…"
            className="w-full px-2.5 py-1.5 text-xs rounded bg-[#161b22] border border-[#30363d] text-[#e6edf3] placeholder-[#484f58] focus:border-[#1f6feb] outline-none"
          />
        </div>

        <div className="p-3 border-b border-[#30363d]">
          <div className="text-[10px] font-semibold text-[#7d8590] uppercase tracking-wider mb-2">
            Nodes
          </div>
          <FilterRow label="Classes"    count={classCount}  checked={showFilters.class}     onChange={() => setShowFilters(f => ({...f, class: !f.class}))}         color="#3fb950" />
          <FilterRow label="Interfaces" count={ifaceCount}  checked={showFilters.interface} onChange={() => setShowFilters(f => ({...f, interface: !f.interface}))} color="#1f6feb" />
          <FilterRow label="Enums"      count={enumCount}   checked={showFilters.enum}      onChange={() => setShowFilters(f => ({...f, enum: !f.enum}))}           color="#a371f7" />
          <FilterRow label="Methods"    count={methodCount} checked={showFilters.method}    onChange={() => setShowFilters(f => ({...f, method: !f.method}))}       color="#1f6feb" />
          <FilterRow label="Fields"     count={fieldCount}  checked={showFilters.field}     onChange={() => setShowFilters(f => ({...f, field: !f.field}))}         color="#7d8590" />
        </div>

        <div className="p-3 border-b border-[#30363d]">
          <div className="text-[10px] font-semibold text-[#7d8590] uppercase tracking-wider mb-2">
            Relations
          </div>
          <FilterRow label="Extends"      checked={edgeFilter.extends}      onChange={() => setEdgeFilter(f => ({...f, extends: !f.extends}))}           color="#f78166" small />
          <FilterRow label="Implements"   checked={edgeFilter.implements}   onChange={() => setEdgeFilter(f => ({...f, implements: !f.implements}))}     color="#a371f7" small />
          <FilterRow label="Imports"      checked={edgeFilter.imports}      onChange={() => setEdgeFilter(f => ({...f, imports: !f.imports}))}           color="#484f58" small />
          <FilterRow label="Calls"        checked={edgeFilter.calls}        onChange={() => setEdgeFilter(f => ({...f, calls: !f.calls}))}               color="#3fb950" small />
          <FilterRow label="Method calls" checked={edgeFilter.method_calls} onChange={() => setEdgeFilter(f => ({...f, method_calls: !f.method_calls}))} color="#3fb950" small />
          <FilterRow label="Contains"     checked={edgeFilter.contains}     onChange={() => setEdgeFilter(f => ({...f, contains: !f.contains}))}         color="#1f6feb" small />
          <FilterRow label="Field type"   checked={edgeFilter.field_type}   onChange={() => setEdgeFilter(f => ({...f, field_type: !f.field_type}))}     color="#7d8590" small />
          <FilterRow label="Param type"   checked={edgeFilter.param_type}   onChange={() => setEdgeFilter(f => ({...f, param_type: !f.param_type}))}     color="#d29922" small />
          <FilterRow label="Return type"  checked={edgeFilter.return_type}  onChange={() => setEdgeFilter(f => ({...f, return_type: !f.return_type}))}   color="#d29922" small />
        </div>

        <div className="p-3 mt-auto">
          <div className="text-[10px] mono text-[#484f58] text-center">
            {classCount}C · {methodCount}M · {fieldCount}F
          </div>
        </div>
      </aside>

      {/* CANVAS */}
      <div className="flex-1 relative">
        <ReactFlow
          nodes={styledNodes}
          edges={edges}
          onNodesChange={onNodesChange}
          onEdgesChange={onEdgesChange}
          onNodeClick={onNodeClick}
          nodeTypes={nodeTypes}
          fitView
          proOptions={{ hideAttribution: true }}
          minZoom={0.15}
          maxZoom={2.5}
        >
          <Background color="#21262d" gap={22} size={1} />
          <Controls className="!bg-[#161b22] !border-[#30363d] !shadow-xl" showInteractive={false} />
          <MiniMap
            className="!bg-[#161b22] !border-[#30363d]"
            nodeColor={(n) => NODE_STYLE[n.data.type]?.accent || '#3fb950'}
            maskColor="rgba(13, 17, 23, 0.85)"
            pannable zoomable
          />
        </ReactFlow>
      </div>

      {/* RIGHT SIDEBAR — selected node details */}
      {selectedNode && (
        <aside className="w-72 border-l border-[#30363d] bg-[#0d1117] overflow-y-auto shrink-0">
          <div className="p-3 border-b border-[#30363d] flex items-start justify-between">
            <div className="min-w-0 flex-1">
              <div className="text-sm font-semibold text-[#e6edf3] truncate">{selectedNode.label}</div>
              <span className={`inline-block mt-2 text-[9px] font-bold px-1.5 py-0.5 rounded uppercase tracking-wide ${NODE_STYLE[selectedNode.type]?.badge}`}>
                {selectedNode.type}
              </span>
            </div>
            <button onClick={() => setSelectedNode(null)} className="text-[#7d8590] hover:text-[#e6edf3] text-lg leading-none">×</button>
          </div>

          <div className="p-3 space-y-3">
            {selectedNode.filePath && (
              <DetailRow label="File" value={selectedNode.filePath} mono truncate />
            )}
            <DetailRow label="Lines" value={selectedNode.lineCount} mono />
            {selectedNode.parentId && (
              <DetailRow label="Parent" value={selectedNode.parentId} mono truncate />
            )}
          </div>

          <div className="p-3 border-t border-[#30363d]">
            <div className="text-[10px] font-semibold text-[#7d8590] uppercase tracking-wider mb-2">
              Incoming
            </div>
            <NeighborList edges={rfEdges.filter((e) => e.target === selectedNode.id)} nodes={styledNodes} side="source" />
          </div>

          <div className="p-3 border-t border-[#30363d]">
            <div className="text-[10px] font-semibold text-[#7d8590] uppercase tracking-wider mb-2">
              Outgoing
            </div>
            <NeighborList edges={rfEdges.filter((e) => e.source === selectedNode.id)} nodes={styledNodes} side="target" />
          </div>
        </aside>
      )}
    </div>
  )
}

/* ------- Small helpers ------- */
function FilterRow({ label, count, checked, onChange, color, small }) {
  return (
    <label className={`flex items-center gap-2 cursor-pointer ${small ? 'py-0.5' : 'py-1'}`}>
      <input type="checkbox" checked={checked} onChange={onChange} className="sr-only" />
      <span
        className="w-3.5 h-3.5 rounded border flex items-center justify-center shrink-0"
        style={{
          borderColor: checked ? color : '#30363d',
          backgroundColor: checked ? `${color}25` : 'transparent',
        }}
      >
        {checked && (
          <svg width="8" height="8" viewBox="0 0 8 8" fill="none">
            <path d="M1 4L3 6L7 2" stroke={color} strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"/>
          </svg>
        )}
      </span>
      <span className="text-xs text-[#8b949e] flex-1 truncate">{label}</span>
      {count != null && <span className="text-[10px] mono text-[#484f58]">{count}</span>}
    </label>
  )
}

function DetailRow({ label, value, mono, truncate }) {
  return (
    <div>
      <div className="text-[9px] text-[#7d8590] uppercase tracking-wider mb-0.5">{label}</div>
      <div className={`text-xs text-[#e6edf3] ${mono ? 'mono' : ''} ${truncate ? 'truncate' : 'break-words'}`} title={value}>
        {value}
      </div>
    </div>
  )
}

function NeighborList({ edges, nodes, side }) {
  if (edges.length === 0) return <div className="text-[11px] text-[#484f58] italic">None</div>
  return (
    <div className="space-y-1">
      {edges.map((e) => {
        const nid = side === 'source' ? e.source : e.target
        const node = nodes.find((n) => n.id === nid)
        const t = e.data?.edgeType
        return (
          <div key={e.id} className="flex items-center gap-2 text-[11px]">
            <span className="w-1.5 h-1.5 rounded-full shrink-0" style={{ backgroundColor: EDGE_COLORS[t] || '#484f58' }} />
            <span className="text-[#e6edf3] truncate flex-1">{node?.data?.label || nid}</span>
            <span className="text-[9px] mono text-[#7d8590] uppercase">{t}</span>
          </div>
        )
      })}
    </div>
  )
}

/* ------- Public export ------- */
export default function DependencyGraph({ graph }) {
  if (!graph || !graph.nodes || graph.nodes.length === 0) {
    return (
      <div className="h-full flex items-center justify-center text-center">
        <div className="max-w-md px-6">
          <div className="w-16 h-16 rounded-full bg-[#161b22] border border-[#30363d] flex items-center justify-center mx-auto mb-4">
            <svg className="w-8 h-8 text-[#484f58]" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M9 3v2m6-2v2M9 19v2m6-2v2M5 9H3m2 6H3m18-6h-2m2 6h-2M7 19h10a2 2 0 002-2V7a2 2 0 00-2-2H7a2 2 0 00-2 2v10a2 2 0 002 2z" />
            </svg>
          </div>
          <h3 className="text-sm font-semibold text-[#e6edf3] mb-2">No graph yet</h3>
          <p className="text-xs text-[#7d8590]">Analyze a Java repository to build its dependency graph.</p>
        </div>
      </div>
    )
  }

  return (
    <ReactFlowProvider>
      <GraphInner graph={graph} />
    </ReactFlowProvider>
  )
}