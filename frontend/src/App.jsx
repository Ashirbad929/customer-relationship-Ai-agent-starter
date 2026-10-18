import { useEffect, useMemo, useRef, useState } from 'react';

const HISTORY_KEY = 'relationship-lookup-history';

function readHistory() {
  try { return JSON.parse(localStorage.getItem(HISTORY_KEY) || '[]'); }
  catch { return []; }
}

async function fetchCustomerData(path, customerId) {
  const response = await fetch(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ customerId }),
  });
  const payload = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(payload.detail || payload.message || payload.error || `Lookup failed (${response.status})`);
  return payload;
}

function formatInr(value) {
  return value == null ? '—' : new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 }).format(value);
}

function requestedGraphTopics(question) {
  const asksForSpend = /\b(spend|spending|spent|transaction|transactions|purchase|purchases|merchant)\b/i.test(question);
  return {
    cards: /\b(card|cards|tier|premium|credit limit|available credit|product)\b/i.test(question),
    bills: /\b(bill|bills|billing|statement|statements|payment|payments|paid|due|owed|owe)\b/i.test(question),
    loans: /\b(loan|loans|emi|borrow|borrowed|mortgage|installment|outstanding)\b/i.test(question),
    asksForSpend,
  };
}

function DataTable({ columns, rows, emptyMessage }) {
  if (!rows?.length) return <div className="panel-empty">{emptyMessage}</div>;
  return <div className="data-table-wrap"><table className="data-table"><thead><tr>{columns.map((column) => <th key={column.key}>{column.label}</th>)}</tr></thead><tbody>{rows.map((row, index) => <tr key={row.id || row.accountId || row.billId || row.loanId || index}>{columns.map((column) => <td key={column.key}>{column.render ? column.render(row) : row[column.key] || '—'}</td>)}</tr>)}</tbody></table></div>;
}

function RelationshipGraph({ data, details }) {
  const [zoom, setZoom] = useState(1);
  const accounts = data.accounts || [];
  const connections = data.possibleConnections || [];
  const cards = details.cards || [];
  const bills = details.bills || [];
  const loans = details.loans || [];
  const detailNodes = [
    ...cards.map((card) => ({ key: `card-${card.accountId}`, accountId: card.accountId, kind: 'card-node', title: card.productName || 'Credit card', subtitle: `${card.tier || 'Card'} · ${card.demoCardStatus || 'Details linked'} · demo limit ${formatInr(card.demoCreditLimitInr)}` })),
    ...bills.map((bill) => ({ key: `bill-${bill.billId}`, accountId: bill.accountId, kind: 'bill-node', title: `Statement ${bill.statementMonth || ''}`, subtitle: `${bill.billId} · due ${formatInr(bill.totalDueInr)} · ${bill.status || 'Status unavailable'}` })),
    ...loans.map((loan) => ({ key: `loan-${loan.loanId}`, accountId: loan.accountId, kind: 'loan-node', title: `${loan.loanType || 'Loan'} loan`, subtitle: `${loan.status || 'Status unavailable'} · demo outstanding ${formatInr(loan.demoOutstandingInr)} · EMI ${formatInr(loan.demoEmiInr)}` })),
  ];
  const rows = Math.max(accounts.length, connections.length, detailNodes.length, 1);
  const height = Math.max(390, (rows + 1) * 88);
  const centerY = height / 2;
  const position = (index, count) => height * (index + 1) / (count + 1);
  const customerName = [data.customer?.firstName, data.customer?.lastName].filter(Boolean).join(' ') || data.customer?.customerId;

  return <div className="graph-canvas">
    <div className="graph-zoom" aria-label="Graph zoom controls"><button type="button" aria-label="Zoom out" onClick={() => setZoom((value) => Math.max(0.7, +(value - 0.15).toFixed(2)))} disabled={zoom <= 0.7}>−</button><span>{Math.round(zoom * 100)}%</span><button type="button" aria-label="Zoom in" onClick={() => setZoom((value) => Math.min(1.8, +(value + 0.15).toFixed(2)))} disabled={zoom >= 1.8}>+</button><button type="button" className="zoom-fit" onClick={() => setZoom(1)}>Fit</button></div>
    <svg style={{ width: `${zoom * 100}%` }} viewBox={`0 0 1220 ${height}`} role="img" aria-label={`Relationship graph for ${customerName}`}>
      {accounts.map((account, index) => {
        const y = position(index, accounts.length);
        return <g key={`edge-${account.accountId}`}>
          <line className="graph-edge account-edge" x1="320" y1={centerY} x2="235" y2={y} />
          <text className="edge-label" x="270" y={(centerY + y) / 2 - 5}>{account.role || 'HOLDS'}</text>
          <rect className="graph-node account-node" x="25" y={y - 29} width="210" height="58" rx="9" />
          <text className="node-title" x="40" y={y - 5}>{account.accountId}</text>
          <text className="node-subtitle" x="40" y={y + 13}>{account.accountType || 'Account'}</text>
        </g>;
      })}
      {detailNodes.map((node, index) => {
        const y = position(index, detailNodes.length);
        const accountIndex = accounts.findIndex((account) => account.accountId === node.accountId);
        const accountY = accountIndex < 0 ? centerY : position(accountIndex, accounts.length);
        return <g key={node.key}>
          <line className={`graph-edge detail-edge ${node.kind}`} x1="235" y1={accountY} x2="620" y2={y} />
          <text className="edge-label" x="430" y={(accountY + y) / 2 - 5}>{node.kind === 'loan-node' ? 'HAS_LOAN_DETAILS' : node.kind === 'bill-node' ? 'HAS_BILL' : 'HAS_PRODUCT'}</text>
          <rect className={`graph-node detail-node ${node.kind}`} x="620" y={y - 31} width="250" height="62" rx="9" />
          <text className="node-title" x="635" y={y - 7}>{node.title}</text>
          <text className="node-subtitle" x="635" y={y + 12}>{node.subtitle}</text>
        </g>;
      })}
      {connections.map((connection, index) => {
        const y = position(index, connections.length);
        const name = [connection.firstName, connection.lastName].filter(Boolean).join(' ') || connection.customerId;
        const evidenceLabel = (connection.traversalEvidence || []).map((item) => `${item.relationshipType} ×${item.sharedRecordCount}`).join(', ') || (connection.evidenceTypes || []).join(', ');
        return <g key={`connection-${connection.customerId}`}>
          <line className="graph-edge connection-edge" x1="550" y1={centerY} x2="950" y2={y} />
          <text className="edge-label" x="770" y={(centerY + y) / 2 - 5}>{evidenceLabel || 'shared record'}</text>
          <rect className="graph-node connection-node" x="950" y={y - 29} width="240" height="58" rx="9" />
          <text className="node-title" x="965" y={y - 5}>{name}</text>
          <text className="node-subtitle" x="965" y={y + 13}>{connection.customerId} · possible link</text>
        </g>;
      })}
      <rect className="graph-node customer-node" x="320" y={centerY - 35} width="230" height="70" rx="12" />
      <text className="customer-node-label" x="340" y={centerY - 8}>{customerName}</text>
      <text className="customer-node-id" x="340" y={centerY + 14}>{data.customer?.customerId} · selected customer</text>
      {accounts.length === 0 && connections.length === 0 && detailNodes.length === 0 && <text className="graph-empty-label" x="610" y={height - 32} textAnchor="middle">No linked accounts or possible connections found.</text>}
    </svg>
    <div className="graph-legend"><span><i className="legend-customer" /> Selected customer</span><span><i className="legend-account" /> Account</span><span><i className="legend-card" /> Card / product</span><span><i className="legend-loan" /> Loan</span><span><i className="legend-bill" /> Bill statement</span><span><i className="legend-connection" /> Possible connection</span></div>
  </div>;
}

function ModelVisualization({ spec }) {
  const [zoom, setZoom] = useState(1);
  const nodeList = (spec?.nodes || []).filter((node) => node?.id && Number.isFinite(Number(node.x)) && Number.isFinite(Number(node.y)));
  const ids = new Set(nodeList.map((node) => node.id));
  const edges = (spec?.edges || []).filter((edge) => ids.has(edge.source) && ids.has(edge.target));
  const nodeById = new Map(nodeList.map((node) => [node.id, node]));
  if (!nodeList.length) return null;
  const point = (node) => ({ x: Math.max(100, Math.min(1000, Number(node.x) + 50)), y: Math.max(45, Math.min(715, Number(node.y) + 30)) });
  const safeType = (type) => String(type || 'record').toLowerCase().replace(/[^a-z0-9_-]/g, '-');

  return <div className="model-visualization">
    <div className="model-visualization-heading"><div><strong>{spec.title || 'Chat-generated graph'}</strong><small>{spec.layout || 'Arrangement chosen from your prompt'}</small></div><div className="graph-zoom" aria-label="Visualization zoom controls"><button type="button" aria-label="Zoom out" onClick={() => setZoom((value) => Math.max(0.7, +(value - 0.15).toFixed(2)))} disabled={zoom <= 0.7}>−</button><span>{Math.round(zoom * 100)}%</span><button type="button" aria-label="Zoom in" onClick={() => setZoom((value) => Math.min(1.8, +(value + 0.15).toFixed(2)))} disabled={zoom >= 1.8}>+</button><button type="button" className="zoom-fit" onClick={() => setZoom(1)}>Fit</button></div></div>
    <div className="model-visualization-canvas"><svg style={{ width: `${zoom * 100}%` }} viewBox="0 0 1100 760" role="img" aria-label={spec.title || 'Graph created from the chat prompt'}>
      {edges.map((edge, index) => {
        const from = point(nodeById.get(edge.source));
        const to = point(nodeById.get(edge.target));
        return <g key={`${edge.source}-${edge.target}-${index}`}><line className="model-graph-edge" x1={from.x} y1={from.y} x2={to.x} y2={to.y} /><text className="model-graph-edge-label" x={(from.x + to.x) / 2} y={(from.y + to.y) / 2 - 5}>{edge.label || ''}</text></g>;
      })}
      {nodeList.map((node) => {
        const { x, y } = point(node);
        return <g key={node.id}><title>{node.label || node.id} · {node.type || 'record'}</title><rect className={`model-graph-node type-${safeType(node.type)}`} x={x - 95} y={y - 29} width="190" height="58" rx="9" /><text className="model-graph-node-label" x={x} y={y - 3} textAnchor="middle">{String(node.label || node.id).slice(0, 26)}</text><text className="model-graph-node-type" x={x} y={y + 14} textAnchor="middle">{String(node.type || 'record').slice(0, 26)}</text></g>;
      })}
    </svg></div>
    <div className="model-visualization-footnote">Visualization is arranged by the AI from records retrieved through the customer graph.</div>
  </div>;
}

export default function App() {
  const [customerId, setCustomerId] = useState('C0002');
  const [data, setData] = useState(null);
  const [history, setHistory] = useState(readHistory);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [chatMessages, setChatMessages] = useState([]);
  const [chatInput, setChatInput] = useState('');
  const [chatPending, setChatPending] = useState(false);
  const [chatError, setChatError] = useState('');
  const chatTranscriptRef = useRef(null);
  const [graphStatus, setGraphStatus] = useState('checking');
  const [activeSection, setActiveSection] = useState('relationships');
  const [sectionCache, setSectionCache] = useState({});
  const [sectionLoading, setSectionLoading] = useState('');
  const [sectionError, setSectionError] = useState('');
  const [graphDetails, setGraphDetails] = useState({ cards: [], bills: [], loans: [] });
  const [graphUpdateNote, setGraphUpdateNote] = useState('');

  useEffect(() => {
    fetch('/api/graph/health')
      .then((response) => { if (!response.ok) throw new Error(); return response.json(); })
      .then(() => setGraphStatus('connected'))
      .catch(() => setGraphStatus('unavailable'));
  }, []);

  useEffect(() => {
    const transcript = chatTranscriptRef.current;
    if (transcript) transcript.scrollTop = transcript.scrollHeight;
  }, [chatMessages, chatPending, chatError]);

  const rankedAccounts = useMemo(() => [...(data?.accounts || [])].sort((a, b) => (b.holderCount || 0) - (a.holderCount || 0)), [data]);
  const maxHolders = Math.max(1, ...rankedAccounts.map((account) => account.holderCount || 0));

  async function selectSection(section) {
    setActiveSection(section);
    setSectionError('');
    const selectedId = data?.customer?.customerId || customerId.trim();
    if (!selectedId || section === 'relationships' || sectionCache[selectedId]?.[section]) return;
    const requests = {
      cards: [['cards', '/api/customers/cards'], ['bills', '/api/customers/bills']],
      loans: [['loans', '/api/customers/loans']],
      contact: [['addresses', '/api/customers/addresses'], ['phoneNumbers', '/api/customers/phone-numbers'], ['emailAddresses', '/api/customers/email-addresses']],
    }[section];
    if (!requests) return;
    setSectionLoading(section);
    try {
      const results = await Promise.all(requests.map(async ([key, path]) => [key, await fetchCustomerData(path, selectedId)]));
      setSectionCache((current) => ({ ...current, [selectedId]: { ...current[selectedId], [section]: Object.fromEntries(results) } }));
    } catch (requestError) {
      setSectionError(requestError.message || `Could not load ${section} data.`);
    } finally { setSectionLoading(''); }
  }

  async function lookup(id = customerId) {
    const selectedId = id.trim();
    if (!selectedId || loading) return;
    setCustomerId(selectedId);
    setError('');
    setLoading(true);
    setActiveSection('relationships');
    setSectionError('');
    try {
      const payload = await fetchCustomerData('/api/customers/relationships', selectedId);
      setData(payload);
      if (data?.customer?.customerId !== payload.customer?.customerId) {
        setChatMessages([]);
        setGraphDetails({ cards: [], bills: [], loans: [] });
        setGraphUpdateNote('');
      }
      setChatError('');
      const nextHistory = [{ customerId: selectedId, lookedUpAt: new Date().toISOString() }, ...history.filter((item) => item.customerId !== selectedId)].slice(0, 12);
      setHistory(nextHistory);
      localStorage.setItem(HISTORY_KEY, JSON.stringify(nextHistory));
    } catch (lookupError) {
      setData(null);
      setError(lookupError.message || 'Unable to load this customer.');
    } finally { setLoading(false); }
  }

  function handleSubmit(event) { event.preventDefault(); lookup(); }

  async function updateGraphForChatQuestion(question, selectedId, currentDetails) {
    const requested = requestedGraphTopics(question);
    const endpoints = [];
    if (requested.cards) endpoints.push(['cards', '/api/customers/cards']);
    if (requested.bills) endpoints.push(['bills', '/api/customers/bills']);
    if (requested.loans) endpoints.push(['loans', '/api/customers/loans']);
    if (!endpoints.length) return currentDetails;
    setActiveSection('relationships');
    setGraphUpdateNote('Updating graph with the data requested in chat…');
    try {
      const resultEntries = await Promise.all(endpoints.map(async ([key, path]) => {
        const response = await fetchCustomerData(path, selectedId);
        return [key, response[key] || []];
      }));
      const nextDetails = { ...currentDetails, ...Object.fromEntries(resultEntries) };
      setGraphDetails(nextDetails);
      setGraphUpdateNote(requested.asksForSpend
        ? 'Statement records are shown in the graph panel. The assistant can separately analyze synthetic transaction and merchant paths from FalkorDB.'
        : 'Graph expanded with the requested customer records. Financial values marked demo are synthetic sample data.');
      return nextDetails;
    } catch (requestError) {
      setGraphUpdateNote(`The chat answer is ready, but the graph could not load those records: ${requestError.message}`);
      return currentDetails;
    }
  }

  async function sendChat(event) {
    event.preventDefault();
    const question = chatInput.trim();
    const selectedId = data?.customer?.customerId || customerId.trim();
    if (!question || !selectedId || chatPending) return;
    setChatMessages((current) => [...current, { role: 'user', content: question }]);
    setChatInput('');
    setChatError('');
    setChatPending(true);
    try {
      let relationshipData = data?.customer?.customerId === selectedId ? data : null;
      let detailsForCustomer = relationshipData ? graphDetails : { cards: [], bills: [], loans: [] };
      if (data?.customer?.customerId !== selectedId) {
        relationshipData = await fetchCustomerData('/api/customers/relationships', selectedId);
        setData(relationshipData);
        setActiveSection('relationships');
        setGraphDetails({ cards: [], bills: [], loans: [] });
        setGraphUpdateNote('');
      }
      const response = await fetch('/api/agent/chat', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ customerId: selectedId, message: question }),
      });
      const payload = await response.json().catch(() => ({}));
      if (!response.ok) throw new Error(payload.detail || payload.message || payload.error || `Chat request failed (${response.status})`);
      detailsForCustomer = await updateGraphForChatQuestion(question, selectedId, detailsForCustomer);
      setChatMessages((current) => [...current, {
        role: 'assistant',
        content: payload.answer || 'The assistant returned an empty answer.',
        visualization: payload.visualization || null,
      }]);
    } catch (chatRequestError) {
      setChatError(chatRequestError.message || 'Unable to reach the agent.');
    } finally { setChatPending(false); }
  }

  const chatPanel = <section className="panel chat-panel"><div className="panel-heading"><div><h2>Customer assistant</h2><p>Ask about relationships, transactions, cards, bills, or loans</p></div><span className="chat-customer-id">{data?.customer?.customerId || customerId || 'Enter ID'}</span></div>
    <div ref={chatTranscriptRef} className="chat-transcript" aria-live="polite" aria-busy={chatPending}>
      {!chatMessages.length && !chatPending && <div className="chat-empty">
        <div className="chat-empty-intro"><strong>Explore this customer’s banking relationships</strong><p>Ask in plain language. The assistant retrieves relevant records from FalkorDB and can build an inline graph arranged to match your instructions.</p></div>
        <div className="chat-capabilities">
          <div><strong>Accounts & connections</strong><span>Account types, customer roles, joint holders, and possible links from shared address, phone, or email records.</span></div>
          <div><strong>Contact details</strong><span>Linked addresses, phone numbers, and email addresses recorded for the customer.</span></div>
          <div><strong>Cards & bills</strong><span>Card products, tiers, demo limits, statement amounts, payment status, and due dates.</span></div>
          <div><strong>Transaction paths</strong><span>Trace synthetic account activity to merchants and find customers with overlapping merchant records.</span></div>
          <div><strong>Loans</strong><span>Loan type and status, demo outstanding balance, EMI, and next due date.</span></div>
          <div><strong>ATM & branch search</strong><span>Ask for nearby public listings using the customer’s city and postal code. Web search needs a configured provider key.</span></div>
          <div><strong>Custom graph views</strong><span>Ask for a top-to-bottom map, group by account, or focus on a relationship or product type.</span></div>
        </div>
        <div className="chat-suggestions-label">TRY A PROMPT</div>
        <div className="chat-suggestions">
          {[
            'Summarize the customer’s accounts and explain each possible connection.',
            'Draw a top-to-bottom graph of accounts and possible connections.',
            'Show the linked address and find a nearby ATM.',
            'Map the customer’s loans and connected accounts.',
            'Compare this customer’s merchant activity with others and explain the graph path.',
          ].map((prompt) => <button type="button" key={prompt} onClick={() => setChatInput(prompt)}>{prompt}</button>)}
        </div>
        <div className="chat-data-note">Shared records are signals for review, not proof of family ties. Supplemental card, bill, and loan values are synthetic demo data.</div>
      </div>}
      {chatMessages.map((item, index) => <div className={`chat-entry ${item.role}`} key={`${index}-${item.role}`}><span>{item.role === 'user' ? 'You' : 'Assistant'}</span><p>{item.content}</p>{item.visualization && <ModelVisualization spec={item.visualization} />}</div>)}
      {chatPending && <div className="chat-waiting"><span className="chat-waiting-avatar">R</span><div className="chat-waiting-copy"><strong>Tracing the customer graph</strong><span>Checking linked records and preparing your answer</span></div><div className="chat-waiting-dots" aria-label="Working"><i /><i /><i /></div></div>}
    </div>
    {chatError && <div className="chat-error" role="alert">{chatError}</div>}
    <form className="chat-form" onSubmit={sendChat}><textarea aria-label="Message the customer assistant" value={chatInput} onChange={(event) => setChatInput(event.target.value)} onKeyDown={(event) => { if (event.key === 'Enter' && !event.shiftKey) { event.preventDefault(); sendChat(event); } }} placeholder="Ask a question…" rows={2} disabled={chatPending} /><button type="submit" aria-label="Send message" disabled={chatPending || !chatInput.trim() || !(data?.customer?.customerId || customerId.trim())}>{chatPending ? '…' : '↑'}</button></form>
    <div className="chat-footnote">AI answers may need verification. Chat requires an enabled agent and available API credits.</div>
  </section>;

  return <div className="app-shell">
    <aside className="sidebar">
      <div className="brand"><div className="brand-mark">R</div><span>Relationship Lookup</span></div>
      <button className="new-lookup" onClick={() => { setData(null); setError(''); setCustomerId(''); }}><span>＋</span> New lookup</button>
      <div className="history-heading"><span>HISTORY</span><span>{history.length}</span></div>
      <div className="history-list">
        {history.length ? history.map((item) => <button key={item.customerId} className={`history-item ${data?.customer?.customerId === item.customerId ? 'selected' : ''}`} onClick={() => lookup(item.customerId)}><span className="history-avatar">⌕</span><span className="history-copy"><strong>{item.customerId}</strong><small>{new Date(item.lookedUpAt).toLocaleDateString()}</small></span></button>) : <p className="history-empty">Your recent customer lookups will appear here.</p>}
      </div>
      <div className="sidebar-footer">Lookups are stored in this browser only.</div>
    </aside>

    <main className="main-panel">
      <header className="topbar"><div className="breadcrumb">Customer tools <span>/</span> <strong>Relationship lookup</strong></div><div className={`connection ${graphStatus}`}><i />{graphStatus === 'checking' ? 'Checking FalkorDB' : graphStatus === 'connected' ? 'FalkorDB connected' : 'FalkorDB unavailable'}</div></header>
      <div className="page-content">
        <section className="page-heading"><div><div className="overline">CUSTOMER GRAPH</div><h1>Relationship lookup</h1><p>Explore account links, possible customer connections, and account holder counts.</p></div></section>

        <form className="lookup-form" onSubmit={handleSubmit}><label htmlFor="customer-id">Customer ID</label><input id="customer-id" value={customerId} onChange={(event) => setCustomerId(event.target.value)} placeholder="Enter a customer ID" /><button type="submit" disabled={loading || !customerId.trim()}>{loading ? <span className="button-spinner" /> : 'Load customer'}</button></form>

        {data && <nav className="section-tabs" aria-label="Customer data views">{[['relationships', 'Relationships'], ['cards', 'Cards & bills'], ['loans', 'Loans'], ['contact', 'Contact details']].map(([key, label]) => <button key={key} type="button" className={activeSection === key ? 'active' : ''} onClick={() => selectSection(key)}>{label}</button>)}</nav>}

        {error && <div className="error-banner"><span>!</span>{error}</div>}
        {sectionError && <div className="error-banner"><span>!</span>{sectionError}</div>}
        {loading && <div className="loading-panel"><span className="loading-spinner" />Loading customer graph…</div>}
        {!loading && sectionLoading && <div className="inline-loading"><span className="loading-spinner" />Loading {sectionLoading === 'cards' ? 'cards and bills' : sectionLoading === 'contact' ? 'contact details' : sectionLoading} from FalkorDB…</div>}

        {!loading && data && activeSection === 'relationships' && <>
          <section className="summary-row">
            <div className="summary-card customer-summary"><span>SELECTED CUSTOMER</span><strong>{[data.customer?.firstName, data.customer?.lastName].filter(Boolean).join(' ') || data.customer?.customerId}</strong><small>{data.customer?.customerId}</small></div>
            <div className="summary-card"><span>LINKED ACCOUNTS</span><strong>{data.accounts?.length || 0}</strong></div>
            <div className="summary-card"><span>POSSIBLE CONNECTIONS</span><strong>{data.possibleConnections?.length || 0}</strong></div>
            <div className="summary-card"><span>MOST HELD ACCOUNT</span><strong>{rankedAccounts[0]?.accountId || '—'}</strong><small>{rankedAccounts[0] ? `${rankedAccounts[0].holderCount} account holder${rankedAccounts[0].holderCount === 1 ? '' : 's'}` : 'No linked account'}</small></div>
          </section>

          <section className="chat-center">{chatPanel}</section>
          <div className="dashboard-grid">
            <section className="panel graph-panel"><div className="panel-heading"><div><h2>Relationship graph</h2><p>Chat can expand this view with linked cards, bills, and loans</p></div><span className="panel-count">{(data.accounts?.length || 0) + (data.possibleConnections?.length || 0) + graphDetails.cards.length + graphDetails.bills.length + graphDetails.loans.length} nodes</span></div><RelationshipGraph data={data} details={graphDetails} />{graphUpdateNote && <div className="graph-update-note">{graphUpdateNote}</div>}</section>
            <section className="panel analytics-panel"><div className="panel-heading"><div><h2>Account holders</h2><p>Accounts ranked by number of linked customers</p></div></div>
                {rankedAccounts.length ? <div className="account-list">{rankedAccounts.map((account, index) => <div className="account-row" key={account.accountId}><div className="account-row-title"><span className="rank">{String(index + 1).padStart(2, '0')}</span><div><strong>{account.accountId}</strong><small>{account.accountType || 'Account'} · Your role: {account.role || '—'}</small></div><b>{account.holderCount}</b></div><div className="bar-track"><div className={`bar-fill ${index === 0 ? 'top-account' : ''}`} style={{ width: `${Math.max(5, ((account.holderCount || 0) / maxHolders) * 100)}%` }} /></div><div className="bar-caption">{account.holderCount} linked holder{account.holderCount === 1 ? '' : 's'}</div></div>)}</div> : <div className="panel-empty">No linked accounts found for this customer.</div>}
                <div className="analytics-note">A holder is a customer connected to the account by a <code>HOLDS</code> relationship.</div>
            </section>
          </div>
      {!!data.possibleConnections?.length && <section className="panel connections-panel"><div className="panel-heading"><div><h2>Possible connections</h2><p>Shared records and joint accounts are signals for review, not proof of a family relationship.</p></div></div><div className="connection-chips">{data.possibleConnections.map((connection) => <div className="connection-chip" key={connection.customerId}><span>{[connection.firstName, connection.lastName].filter(Boolean).join(' ') || connection.customerId}</span><small>{connection.customerId}</small>{(connection.traversalEvidence || []).map((evidence) => <small key={`${evidence.relationshipType}-${evidence.path}`}>{evidence.path} · {evidence.sharedRecordCount} record(s)</small>)}</div>)}</div></section>}
        </>}

        {!loading && data && activeSection !== 'relationships' && <>
          <section className="chat-center">{chatPanel}</section>
          <div className="dashboard-grid detail-dashboard">
          <div className="detail-column">
            {activeSection === 'cards' && <>
              <section className="panel detail-panel"><div className="panel-heading"><div><h2>Credit cards</h2><p>Card tier and account information linked in the graph</p></div></div><DataTable rows={sectionCache[data.customer.customerId]?.cards?.cards?.cards} emptyMessage="No linked card products found. Load the supplemental card datasets into FalkorDB first." columns={[{ key: 'accountId', label: 'Account' }, { key: 'productName', label: 'Product' }, { key: 'tier', label: 'Tier' }, { key: 'role', label: 'Customer role' }, { key: 'demoCreditLimitInr', label: 'Demo limit', render: (row) => formatInr(row.demoCreditLimitInr) }, { key: 'demoAvailableCreditInr', label: 'Demo available', render: (row) => formatInr(row.demoAvailableCreditInr) }, { key: 'demoCardStatus', label: 'Status' }]} /></section>
              <section className="panel detail-panel"><div className="panel-heading"><div><h2>Credit-card bills</h2><p>Statements connected to the customer’s card accounts</p></div></div><DataTable rows={sectionCache[data.customer.customerId]?.cards?.bills?.bills} emptyMessage="No linked billing records found. Load the supplemental billing dataset into FalkorDB first." columns={[{ key: 'billId', label: 'Statement' }, { key: 'accountId', label: 'Account' }, { key: 'statementMonth', label: 'Period' }, { key: 'totalDueInr', label: 'Total due', render: (row) => formatInr(row.totalDueInr) }, { key: 'minimumDueInr', label: 'Minimum due', render: (row) => formatInr(row.minimumDueInr) }, { key: 'amountPaidInr', label: 'Paid', render: (row) => formatInr(row.amountPaidInr) }, { key: 'dueDate', label: 'Due date' }, { key: 'status', label: 'Status' }]} /></section>
              <p className="demo-notice">Supplemental card limits, bill amounts, and statuses are synthetic demo data.</p>
            </>}
            {activeSection === 'loans' && <>
              <section className="panel detail-panel"><div className="panel-heading"><div><h2>Loans</h2><p>Loan accounts and their linked demo details</p></div></div><DataTable rows={sectionCache[data.customer.customerId]?.loans?.loans?.loans} emptyMessage="No linked loan details found. Load the supplemental loan dataset into FalkorDB first." columns={[{ key: 'loanType', label: 'Type' }, { key: 'accountId', label: 'Account' }, { key: 'status', label: 'Status' }, { key: 'demoPrincipalInr', label: 'Demo principal', render: (row) => formatInr(row.demoPrincipalInr) }, { key: 'demoOutstandingInr', label: 'Demo outstanding', render: (row) => formatInr(row.demoOutstandingInr) }, { key: 'demoEmiInr', label: 'Demo EMI', render: (row) => formatInr(row.demoEmiInr) }, { key: 'nextDueDate', label: 'Next due' }]} /></section>
              <p className="demo-notice">Supplemental loan balances, EMIs, and statuses are synthetic demo data.</p>
            </>}
            {activeSection === 'contact' && <section className="contact-grid">
              <div className="panel detail-panel"><div className="panel-heading"><div><h2>Addresses</h2><p>Direct lookup from FalkorDB</p></div></div>{sectionCache[data.customer.customerId]?.contact?.addresses?.addresses?.length ? <div className="contact-list">{sectionCache[data.customer.customerId].contact.addresses.addresses.map((address) => <div key={address.addressId}><strong>{address.line1} {address.unit}</strong><span>{address.city} {address.postalCode}</span><small>{address.addressId}</small></div>)}</div> : <div className="panel-empty">No linked address records found.</div>}</div>
              <div className="panel detail-panel"><div className="panel-heading"><div><h2>Phone numbers</h2><p>Direct lookup from FalkorDB</p></div></div>{sectionCache[data.customer.customerId]?.contact?.phoneNumbers?.phoneNumbers?.length ? <div className="contact-list">{sectionCache[data.customer.customerId].contact.phoneNumbers.phoneNumbers.map((phone) => <div key={phone}><strong>{phone}</strong></div>)}</div> : <div className="panel-empty">No linked phone records found.</div>}</div>
              <div className="panel detail-panel"><div className="panel-heading"><div><h2>Email addresses</h2><p>Direct lookup from FalkorDB</p></div></div>{sectionCache[data.customer.customerId]?.contact?.emailAddresses?.emailAddresses?.length ? <div className="contact-list">{sectionCache[data.customer.customerId].contact.emailAddresses.emailAddresses.map((email) => <div key={email}><strong>{email}</strong></div>)}</div> : <div className="panel-empty">No linked email records found.</div>}</div>
            </section>}
          </div>
          </div>
        </>}

        {!loading && !data && <div className="prelookup-grid"><div className="empty-state"><div className="empty-icon">⌕</div><h2>Start with a customer</h2><p>Enter a customer ID above to load their graph, accounts, and relationship insights.</p></div><section className="chat-center">{chatPanel}</section></div>}
        <footer className="page-footer">Customer data is shown from your connected graph. Verify important information in source systems.</footer>
      </div>
    </main>
  </div>;
}
