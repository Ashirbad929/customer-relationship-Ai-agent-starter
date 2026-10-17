import { useEffect, useMemo, useState } from 'react';

const HISTORY_KEY = 'relationship-lookup-history';

function readHistory() {
  try { return JSON.parse(localStorage.getItem(HISTORY_KEY) || '[]'); }
  catch { return []; }
}

function RelationshipGraph({ data }) {
  const accounts = data.accounts || [];
  const connections = data.possibleConnections || [];
  const rows = Math.max(accounts.length, connections.length, 1);
  const height = Math.max(370, (rows + 1) * 82);
  const centerY = height / 2;
  const position = (index, count) => height * (index + 1) / (count + 1);
  const customerName = [data.customer?.firstName, data.customer?.lastName].filter(Boolean).join(' ') || data.customer?.customerId;

  return <div className="graph-canvas">
    <svg viewBox={`0 0 760 ${height}`} role="img" aria-label={`Relationship graph for ${customerName}`}>
      {accounts.map((account, index) => {
        const y = position(index, accounts.length);
        return <g key={`edge-${account.accountId}`}>
          <line className="graph-edge account-edge" x1="250" y1={centerY} x2="175" y2={y} />
          <text className="edge-label" x="190" y={(centerY + y) / 2 - 5}>{account.role || 'HOLDS'}</text>
          <rect className="graph-node account-node" x="25" y={y - 29} width="150" height="58" rx="9" />
          <text className="node-title" x="40" y={y - 5}>{account.accountId}</text>
          <text className="node-subtitle" x="40" y={y + 13}>{account.accountType || 'Account'}</text>
        </g>;
      })}
      {connections.map((connection, index) => {
        const y = position(index, connections.length);
        const name = [connection.firstName, connection.lastName].filter(Boolean).join(' ') || connection.customerId;
        return <g key={`connection-${connection.customerId}`}>
          <line className="graph-edge connection-edge" x1="510" y1={centerY} x2="615" y2={y} />
          <text className="edge-label" x="535" y={(centerY + y) / 2 - 5}>{(connection.evidenceTypes || []).join(', ') || 'shared record'}</text>
          <rect className="graph-node connection-node" x="590" y={y - 29} width="155" height="58" rx="9" />
          <text className="node-title" x="604" y={y - 5}>{name}</text>
          <text className="node-subtitle" x="604" y={y + 13}>{connection.customerId} · possible link</text>
        </g>;
      })}
      <rect className="graph-node customer-node" x="250" y={centerY - 35} width="260" height="70" rx="12" />
      <text className="customer-node-label" x="270" y={centerY - 8}>{customerName}</text>
      <text className="customer-node-id" x="270" y={centerY + 14}>{data.customer?.customerId} · selected customer</text>
      {accounts.length === 0 && connections.length === 0 && <text className="graph-empty-label" x="380" y={height - 32} textAnchor="middle">No linked accounts or possible connections found.</text>}
    </svg>
    <div className="graph-legend"><span><i className="legend-customer" /> Selected customer</span><span><i className="legend-account" /> Account</span><span><i className="legend-connection" /> Possible connection</span></div>
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
  const [graphStatus, setGraphStatus] = useState('checking');

  useEffect(() => {
    fetch('/api/graph/health')
      .then((response) => { if (!response.ok) throw new Error(); return response.json(); })
      .then(() => setGraphStatus('connected'))
      .catch(() => setGraphStatus('unavailable'));
  }, []);

  const rankedAccounts = useMemo(() => [...(data?.accounts || [])].sort((a, b) => (b.holderCount || 0) - (a.holderCount || 0)), [data]);
  const maxHolders = Math.max(1, ...rankedAccounts.map((account) => account.holderCount || 0));

  async function lookup(id = customerId) {
    const selectedId = id.trim();
    if (!selectedId || loading) return;
    setCustomerId(selectedId);
    setError('');
    setLoading(true);
    try {
      const response = await fetch('/api/customers/relationships', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ customerId: selectedId }),
      });
      const payload = await response.json().catch(() => ({}));
      if (!response.ok) throw new Error(payload.detail || payload.message || payload.error || `Lookup failed (${response.status})`);
      setData(payload);
      if (data?.customer?.customerId !== payload.customer?.customerId) setChatMessages([]);
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
      const response = await fetch('/api/agent/chat', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ customerId: selectedId, message: question }),
      });
      const payload = await response.json().catch(() => ({}));
      if (!response.ok) throw new Error(payload.detail || payload.message || payload.error || `Chat request failed (${response.status})`);
      setChatMessages((current) => [...current, { role: 'assistant', content: payload.answer || 'The assistant returned an empty answer.' }]);
    } catch (chatRequestError) {
      setChatError(chatRequestError.message || 'Unable to reach the agent.');
    } finally { setChatPending(false); }
  }

  const chatPanel = <section className="panel chat-panel"><div className="panel-heading"><div><h2>Customer assistant</h2><p>Ask about this customer’s graph data</p></div><span className="chat-customer-id">{data?.customer?.customerId || customerId || 'Enter ID'}</span></div>
    <div className="chat-transcript" aria-live="polite">
      {!chatMessages.length && !chatPending && <div className="chat-empty">Ask about accounts, possible connections, address, or phone details.</div>}
      {chatMessages.map((item, index) => <div className={`chat-entry ${item.role}`} key={`${index}-${item.role}`}><span>{item.role === 'user' ? 'You' : 'Assistant'}</span><p>{item.content}</p></div>)}
      {chatPending && <div className="chat-waiting"><i /> Thinking…</div>}
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

        {error && <div className="error-banner"><span>!</span>{error}</div>}
        {loading && <div className="loading-panel"><span className="loading-spinner" />Loading customer graph…</div>}

        {!loading && data && <>
          <section className="summary-row">
            <div className="summary-card customer-summary"><span>SELECTED CUSTOMER</span><strong>{[data.customer?.firstName, data.customer?.lastName].filter(Boolean).join(' ') || data.customer?.customerId}</strong><small>{data.customer?.customerId}</small></div>
            <div className="summary-card"><span>LINKED ACCOUNTS</span><strong>{data.accounts?.length || 0}</strong></div>
            <div className="summary-card"><span>POSSIBLE CONNECTIONS</span><strong>{data.possibleConnections?.length || 0}</strong></div>
            <div className="summary-card"><span>MOST HELD ACCOUNT</span><strong>{rankedAccounts[0]?.accountId || '—'}</strong><small>{rankedAccounts[0] ? `${rankedAccounts[0].holderCount} account holder${rankedAccounts[0].holderCount === 1 ? '' : 's'}` : 'No linked account'}</small></div>
          </section>

          <div className="dashboard-grid">
            <section className="panel graph-panel"><div className="panel-heading"><div><h2>Relationship graph</h2><p>Customer, account, and possible connection links</p></div><span className="panel-count">{(data.accounts?.length || 0) + (data.possibleConnections?.length || 0)} links</span></div><RelationshipGraph data={data} /></section>
            <div className="right-column">
              <section className="panel analytics-panel"><div className="panel-heading"><div><h2>Account holders</h2><p>Accounts ranked by number of linked customers</p></div></div>
                {rankedAccounts.length ? <div className="account-list">{rankedAccounts.map((account, index) => <div className="account-row" key={account.accountId}><div className="account-row-title"><span className="rank">{String(index + 1).padStart(2, '0')}</span><div><strong>{account.accountId}</strong><small>{account.accountType || 'Account'} · Your role: {account.role || '—'}</small></div><b>{account.holderCount}</b></div><div className="bar-track"><div className={`bar-fill ${index === 0 ? 'top-account' : ''}`} style={{ width: `${Math.max(5, ((account.holderCount || 0) / maxHolders) * 100)}%` }} /></div><div className="bar-caption">{account.holderCount} linked holder{account.holderCount === 1 ? '' : 's'}</div></div>)}</div> : <div className="panel-empty">No linked accounts found for this customer.</div>}
                <div className="analytics-note">A holder is a customer connected to the account by a <code>HOLDS</code> relationship.</div>
              </section>
              {chatPanel}
            </div>
          </div>
          {!!data.possibleConnections?.length && <section className="panel connections-panel"><div className="panel-heading"><div><h2>Possible connections</h2><p>Shared records are signals for review, not proof of a family relationship.</p></div></div><div className="connection-chips">{data.possibleConnections.map((connection) => <div className="connection-chip" key={connection.customerId}><span>{[connection.firstName, connection.lastName].filter(Boolean).join(' ') || connection.customerId}</span><small>{connection.customerId} · {(connection.evidenceTypes || []).join(', ')}</small></div>)}</div></section>}
        </>}

        {!loading && !data && <div className="prelookup-grid"><div className="empty-state"><div className="empty-icon">⌕</div><h2>Look up a customer</h2><p>Enter a customer ID to view their relationship graph and account holder analytics, or start chatting about the ID above.</p></div>{chatPanel}</div>}
        <footer className="page-footer">Customer data is shown from your connected graph. Verify important information in source systems.</footer>
      </div>
    </main>
  </div>;
}
