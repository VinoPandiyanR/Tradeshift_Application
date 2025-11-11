
const TOKEN_KEY = 'tradeshift.token';
const USERNAME_KEY = 'tradeshift.username';

const isFileProtocol = window.location.protocol === 'file:';
const isLocalhost = window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1';

let API_BASE = 'http://localhost:8080';
if (CONFIG?.api?.baseUrl) {
  API_BASE = CONFIG.api.baseUrl;
} else if (isLocalhost && window.location.port) {
  API_BASE = `http://${window.location.hostname}:8080`;
}

const WS_BASE = API_BASE;

let stompClient = null;
let priceChart = null;
let portfolioData = [];
let updateCount = 0;
let lastPrices = new Map();
let chartUpdateTimeout = null;

function showLoading() {
  document.getElementById('loading-overlay').style.display = 'flex';
}

function hideLoading() {
  document.getElementById('loading-overlay').style.display = 'none';
}

function showAlert(elementId, message, type = 'error') {
  const alert = document.getElementById(elementId);
  if (alert) {
    const escapedMessage = message
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/\n/g, '<br>');
    alert.innerHTML = escapedMessage;
    alert.className = `alert alert-${type}`;
    alert.style.display = 'block';
    setTimeout(() => {
      alert.style.display = 'none';
    }, 8000);
  } else {
    console.error(`Alert element not found: ${elementId}`, message);
    if (type === 'error') {
      alert(message);
    }
  }
}

function showNotification(title, message, type = 'info', duration = 5000) {
  showToast(title, message, type, duration);
  
  if (type === 'error') {
    console.error(`${title}: ${message}`);
  } else if (type === 'success') {
    console.log(`✅ ${title}: ${message}`);
  } else {
    console.log(`ℹ️ ${title}: ${message}`);
  }
}

function setAuth(token, username) {
  localStorage.setItem(TOKEN_KEY, token);
  localStorage.setItem(USERNAME_KEY, username);
  currentUser = username;
  updateNav();
}

function getAuthToken() {
  return localStorage.getItem(TOKEN_KEY);
}

function clearAuth() {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USERNAME_KEY);
  currentUser = null;
  updateNav();
  if (stompClient && stompClient.connected) {
    stompClient.disconnect();
  }
  updateCount = 0;
  lastPrices.clear();
  showView('login');
}

function updateNav() {
  const token = getAuthToken();
  const username = localStorage.getItem(USERNAME_KEY);
  
  document.getElementById('nav-login').style.display = token ? 'none' : 'block';
  document.getElementById('nav-register').style.display = token ? 'none' : 'block';
  document.getElementById('nav-portfolio').style.display = token ? 'block' : 'none';
  document.getElementById('nav-transactions').style.display = token ? 'block' : 'none';
  document.getElementById('nav-profile').style.display = token ? 'block' : 'none';
  document.getElementById('nav-user').style.display = token ? 'block' : 'none';
  document.getElementById('nav-logout').style.display = token ? 'block' : 'none';
  
  if (username) {
    document.getElementById('nav-user').textContent = `Welcome, ${username}`;
  }
}

function getAuthHeaders() {
  const headers = {};
  const token = getAuthToken();
  if (token) headers['Authorization'] = `Bearer ${token}`;
  return headers;
}

async function apiGet(path) {
  if (isFileProtocol) {
    throw new Error('Cannot connect from file:// protocol. Please serve the frontend using a web server. Run: npm start (or npm run dev) from the frontend directory.');
  }
  
  let res;
  try {
    res = await fetch(`${API_BASE}${path}`, {
      method: 'GET',
      headers: getAuthHeaders(),
      mode: 'cors',
      credentials: 'include'
    });
  } catch (fetchError) {
    let errorMsg = `Network error: ${fetchError.message}`;
    if (fetchError.message.includes('Failed to fetch') || fetchError.message.includes('NetworkError')) {
      errorMsg = `Cannot connect to server at ${API_BASE}. Please ensure the Spring Boot server is running on port 8080.`;
    }
    throw new Error(errorMsg);
  }
  
  const text = await res.text();
  let data;
  try {
    data = text && text.trim() ? JSON.parse(text) : {};
  } catch (e) {
    data = text && text.trim() ? { message: text } : { message: 'Unknown error' };
  }
  
  if (!res.ok) {
    const errorMsg = (data && (data.error || data.message)) || `Request failed: ${res.status}`;
    
    if (res.status === 401) {
      throw new Error(errorMsg || 'Unauthorized. Please check your credentials.');
    }
    if (res.status === 403) {
      throw new Error(errorMsg || 'Access denied. Please check your permissions.');
    }
    if (res.status === 500) {
      console.error('Server Error Details:', {
        status: res.status,
        error: data.error,
        message: data.message,
        exception: data.exception,
        fullResponse: data
      });
      const userMessage = data.message || 'An internal server error occurred';
      throw new Error(`Server Error: ${userMessage}. Please try again later or contact support if the problem persists.`);
    }
    throw new Error(errorMsg);
  }
  return data;
}

async function apiPost(path, body) {
  if (isFileProtocol) {
    throw new Error('Cannot connect from file:// protocol. Please serve the frontend using a web server. Run: npm start (or npm run dev) from the frontend directory.');
  }
  
  const headers = { 'Content-Type': 'application/json' };
  const token = getAuthToken();
  if (token) headers['Authorization'] = `Bearer ${token}`;
  
  let res;
  try {
    res = await fetch(`${API_BASE}${path}`, {
      method: 'POST',
      headers: headers,
      body: JSON.stringify(body),
      mode: 'cors',
      credentials: 'include'
    });
  } catch (fetchError) {
    let errorMsg = `Network error: ${fetchError.message}`;
    
    if (fetchError.message.includes('Failed to fetch') || fetchError.message.includes('NetworkError')) {
      const troubleshooting = [
        'The Spring Boot server is running (mvn spring-boot:run)',
        `The server is accessible at ${API_BASE}`,
        'MySQL and MongoDB are running',
        'CORS is properly configured in SecurityConfig',
        'You are not using file:// protocol (use npm start instead)'
      ];
      
      errorMsg = `Cannot connect to server at ${API_BASE}.\n\nTroubleshooting:\n${troubleshooting.map((t, i) => `${i + 1}. ${t}`).join('\n')}\n\nCheck browser console for more details.`;
      
      console.error('Connection Error Details:');
      console.error('API Base URL:', API_BASE);
      console.error('Current URL:', window.location.href);
      console.error('Protocol:', window.location.protocol);
      console.error('Error:', fetchError);
    }
    
    throw new Error(errorMsg);
  }
  
  const text = await res.text();
  let data;
  try {
    data = text && text.trim() ? JSON.parse(text) : {};
  } catch (e) {
    data = text && text.trim() ? { message: text } : { message: 'Unknown error' };
  }
  
  if (!res.ok) {
    const errorMsg = (data && (data.error || data.message)) || `Request failed: ${res.status}`;
    
    if (res.status === 400) {
      const message = data && data.message ? data.message : errorMsg;
      throw new Error(message);
    }
    if (res.status === 401) {
      throw new Error(errorMsg || 'Invalid username or password');
    }
    if (res.status === 403) {
      throw new Error(errorMsg || 'Access denied. Please check your credentials and permissions.');
    }
    if (res.status === 500) {
      console.error('Server Error Details:', {
        status: res.status,
        error: data.error,
        message: data.message,
        exception: data.exception,
        fullResponse: data
      });
      const userMessage = data.message || 'An internal server error occurred';
      throw new Error(`Server Error: ${userMessage}. Please try again later or contact support if the problem persists.`);
    }
    throw new Error(errorMsg);
  }
  return data;
}

async function apiPut(path, body) {
  if (isFileProtocol) {
    throw new Error('Cannot connect from file:// protocol. Please serve the frontend using a web server. Run: npm start (or npm run dev) from the frontend directory.');
  }
  
  const headers = { 'Content-Type': 'application/json' };
  const token = getAuthToken();
  if (token) headers['Authorization'] = `Bearer ${token}`;
  
  let res;
  try {
    res = await fetch(`${API_BASE}${path}`, {
      method: 'PUT',
      headers: headers,
      body: JSON.stringify(body),
      mode: 'cors',
      credentials: 'include'
    });
  } catch (fetchError) {
    let errorMsg = `Network error: ${fetchError.message}`;
    if (fetchError.message.includes('Failed to fetch') || fetchError.message.includes('NetworkError')) {
      errorMsg = `Cannot connect to server at ${API_BASE}. Please ensure the Spring Boot server is running on port 8080.`;
    }
    throw new Error(errorMsg);
  }
  
  const text = await res.text();
  let data;
  try {
    data = text && text.trim() ? JSON.parse(text) : {};
  } catch (e) {
    data = text && text.trim() ? { message: text } : { message: 'Unknown error' };
  }
  
  if (!res.ok) {
    const errorMsg = (data && (data.error || data.message)) || `Request failed: ${res.status}`;
    
    if (res.status === 400) {
      const message = data && data.message ? data.message : errorMsg;
      throw new Error(message);
    }
    if (res.status === 401) {
      throw new Error(errorMsg || 'Unauthorized. Please check your credentials.');
    }
    if (res.status === 403) {
      throw new Error(errorMsg || 'Access denied. Please check your permissions.');
    }
    if (res.status === 500) {
      console.error('Server Error Details:', {
        status: res.status,
        error: data.error,
        message: data.message,
        exception: data.exception,
        fullResponse: data
      });
      const userMessage = data.message || 'An internal server error occurred';
      throw new Error(`Server Error: ${userMessage}. Please try again later or contact support if the problem persists.`);
    }
    throw new Error(errorMsg);
  }
  return data;
}

async function apiDelete(path) {
  if (isFileProtocol) {
    throw new Error('Cannot connect from file:// protocol. Please serve the frontend using a web server. Run: npm start (or npm run dev) from the frontend directory.');
  }
  
  let res;
  try {
    res = await fetch(`${API_BASE}${path}`, {
      method: 'DELETE',
      headers: getAuthHeaders(),
      mode: 'cors',
      credentials: 'include'
    });
  } catch (fetchError) {
    let errorMsg = `Network error: ${fetchError.message}`;
    if (fetchError.message.includes('Failed to fetch') || fetchError.message.includes('NetworkError')) {
      errorMsg = `Cannot connect to server at ${API_BASE}. Please ensure the Spring Boot server is running on port 8080.`;
    }
    throw new Error(errorMsg);
  }
  
  if (!res.ok) {
    const text = await res.text();
    let data;
    try {
      data = text && text.trim() ? JSON.parse(text) : {};
    } catch (e) {
      data = text && text.trim() ? { message: text } : { message: 'Unknown error' };
    }
    
    const errorMsg = (data && (data.error || data.message)) || `Request failed: ${res.status}`;
    
    if (res.status === 401) {
      throw new Error(errorMsg || 'Unauthorized. Please check your credentials.');
    }
    if (res.status === 403) {
      throw new Error(errorMsg || 'Access denied. Please check your permissions.');
    }
    if (res.status === 500) {
      console.error('Server Error Details:', {
        status: res.status,
        error: data.error,
        message: data.message,
        exception: data.exception,
        fullResponse: data
      });
      const userMessage = data.message || 'An internal server error occurred';
      throw new Error(`Server Error: ${userMessage}. Please try again later or contact support if the problem persists.`);
    }
    throw new Error(errorMsg);
  }
  return res.ok;
}

function showView(viewName) {
  document.querySelectorAll('.view-container').forEach(v => v.style.display = 'none');
  const view = document.getElementById(`view-${viewName}`);
  if (view) {
    view.style.display = 'block';
  }
  
  if (viewName === 'portfolio') {
    loadPortfolio();
  } else if (viewName === 'transactions') {
    loadTransactions();
  } else if (viewName === 'profile') {
    loadProfile();
  }
}

async function loadPortfolio() {
  if (!getAuthToken()) {
    showView('login');
    return;
  }
  
  showView('portfolio');
  showLoading();
  
  initPortfolioTabs();
  
  try {
    await Promise.all([
      fetchBalance(),
      fetchPortfolioData()
    ]);
    
    setTimeout(() => {
      if (getAuthToken() && (!stompClient || !stompClient.connected)) {
        connectWebSocket();
      } else if (getAuthToken() && stompClient && stompClient.connected) {
        subscribeToPortfolioUpdates();
      }
    }, 500);
  } catch (err) {
    showAlert('portfolio-error', 'Failed to load portfolio: ' + err.message);
  } finally {
    hideLoading();
  }
}

async function fetchBalance() {
  try {
    const data = await apiGet(CONFIG.api.endpoints.account.balance);
    const bal = typeof data.balance === 'number' ? data.balance : 0;
    document.getElementById('balance-amount').textContent = `$${bal.toFixed(2)}`;
  } catch (err) {
    console.error('Balance fetch error:', err);
    document.getElementById('balance-amount').textContent = 'Error';
  }
}

async function fetchPortfolioData() {
  const tbody = document.getElementById('portfolio-tbody');
  if (!tbody) return;
  
  const errorAlert = document.getElementById('portfolio-error');
  if (errorAlert) {
    errorAlert.style.display = 'none';
  }
  
  tbody.innerHTML = '<tr><td colspan="8" class="text-center">Loading...</td></tr>';
  
  try {
    const data = await apiGet(CONFIG.api.endpoints.portfolio);
    portfolioData = data || [];
    
    if (portfolioData.length === 0) {
      tbody.innerHTML = '<tr><td colspan="8" class="text-center">No assets in portfolio</td></tr>';
      updatePortfolioChart();
      return;
    }
    
    let totalValue = 0;
    let totalCost = 0;
    let html = '';
    
    portfolioData.forEach(asset => {
      const qty = asset.quantity || 0;
      const buyPrice = asset.avgBuyPrice || 0;
      const marketPrice = asset.marketPrice || asset.currentPrice || 0;
      const currentPrice = asset.currentPrice || marketPrice || 0;
      
      const priceForCalculation = marketPrice > 0 ? marketPrice : currentPrice;
      const marketValue = qty * priceForCalculation;
      const pl = marketValue - (qty * buyPrice);
      const plPercent = buyPrice > 0 ? ((pl / (qty * buyPrice)) * 100) : 0;
      
      totalValue += marketValue;
      totalCost += qty * buyPrice;
      
      const displayPrice = marketPrice > 0 ? marketPrice : currentPrice;
      
      html += `
        <tr>
          <td><strong>${asset.symbol}</strong></td>
          <td>${qty.toFixed(2)}</td>
          <td>$${buyPrice.toFixed(2)}</td>
          <td id="price-${asset.symbol}">$${displayPrice.toFixed(2)}</td>
          <td id="value-${asset.symbol}">$${marketValue.toFixed(2)}</td>
          <td id="pl-${asset.symbol}" class="${pl >= 0 ? 'pl-positive' : 'pl-negative'}">
            ${pl >= 0 ? '+' : ''}$${pl.toFixed(2)}
          </td>
          <td class="${pl >= 0 ? 'pl-positive' : 'pl-negative'}">
            ${plPercent >= 0 ? '+' : ''}${plPercent.toFixed(2)}%
          </td>
          <td>
            <button class="btn btn-danger btn-sm" data-remove="${asset.id}"><i class="fas fa-trash"></i> Remove</button>
          </td>
        </tr>
      `;
    });
    
    tbody.innerHTML = html;
    
    const totalPL = totalValue - totalCost;
    const statsDiv = document.getElementById('portfolio-stats');
    if (statsDiv) {
      statsDiv.innerHTML = `
        <div><strong>Total Value:</strong> <span class="text-success">$${totalValue.toFixed(2)}</span></div>
        <div><strong>Total P/L:</strong> <span class="${totalPL >= 0 ? 'pl-positive' : 'pl-negative'}">
          ${totalPL >= 0 ? '+' : ''}$${totalPL.toFixed(2)}
        </span></div>
      `;
    }
    
    const overviewTotalValue = document.getElementById('overview-total-value');
    const overviewTotalPL = document.getElementById('overview-total-pl');
    const overviewHoldingsCount = document.getElementById('overview-holdings-count');
    
    if (overviewTotalValue) {
      overviewTotalValue.textContent = `$${totalValue.toFixed(2)}`;
    }
    if (overviewTotalPL) {
      overviewTotalPL.textContent = `${totalPL >= 0 ? '+' : ''}$${totalPL.toFixed(2)}`;
      overviewTotalPL.className = totalPL >= 0 ? 'pl-positive' : 'pl-negative';
    }
    if (overviewHoldingsCount) {
      overviewHoldingsCount.textContent = portfolioData.length;
    }
    
    try {
      const summary = await apiGet(CONFIG.api.endpoints.analytics);
        if (statsDiv) {
        statsDiv.innerHTML += `
          <div><strong>Analytics Total:</strong> $${(summary.totalValue || 0).toFixed(2)}</div>
        `;
      }
    } catch (e) {
    }

    document.querySelectorAll('button[data-remove]').forEach(btn => {
      btn.onclick = async () => {
        const row = btn.closest('tr');
        const symbol = row?.querySelector('td:first-child')?.textContent?.trim() || 'this asset';
        const confirmed = confirm(`Are you sure you want to remove ${symbol} from your portfolio?`);
        if (confirmed) {
          try {
            await apiDelete(`${CONFIG.api.endpoints.portfolio}/${btn.dataset.remove}`);
            fetchPortfolioData();
            showNotification('Success!', `Successfully removed ${symbol} from your portfolio`, 'success');
          } catch (err) {
            showNotification('Error', 'Failed to remove asset: ' + err.message, 'error');
          }
        }
      };
    });
    
    setTimeout(() => {
      if (getAuthToken()) {
        if (!stompClient || !stompClient.connected) {
          console.log('Connecting WebSocket for real-time updates...');
          connectWebSocket();
        } else {
          console.log('WebSocket already connected, resubscribing to portfolio updates...');
          subscribeToPortfolioUpdates();
        }
      }
    }, 500);
    updatePortfolioChart();
    
  } catch (err) {
    console.error('Portfolio fetch error:', err);
    tbody.innerHTML = `<tr><td colspan="8" class="text-center text-danger">Error: ${err.message}</td></tr>`;
    showAlert('portfolio-error', `Failed to load portfolio: ${err.message}`);
    if (priceChart) {
      priceChart.data.labels = [];
      priceChart.data.datasets[0].data = [];
      priceChart.update();
    }
  }
}

async function loadTransactions() {
  if (!getAuthToken()) {
    showView('login');
    return;
  }
  
  showView('transactions');
  const tbody = document.getElementById('transactions-tbody');
  if (!tbody) return;
  
  tbody.innerHTML = '<tr><td colspan="6" class="text-center">Loading...</td></tr>';
  
  try {
    const rows = await apiGet(CONFIG.api.endpoints.transactions);
    if (!Array.isArray(rows) || rows.length === 0) {
      tbody.innerHTML = '<tr><td colspan="6" class="text-center">No transactions found</td></tr>';
      return;
    }
    
    tbody.innerHTML = rows.map(t => {
      const ts = t.createdAt || t.time || '';
      const totalValue = (t.quantity || 0) * (t.price || 0);
      const typeClass = t.type === 'BUY' ? 'text-success' : 'text-danger';
      return `
        <tr>
          <td>${ts}</td>
          <td><strong>${t.symbol}</strong></td>
          <td><span class="${typeClass}">${t.type}</span></td>
          <td>${t.quantity}</td>
          <td>$${t.price?.toFixed(2) || '0.00'}</td>
          <td>$${totalValue.toFixed(2)}</td>
        </tr>
      `;
    }).join('');
  } catch (err) {
    tbody.innerHTML = `<tr><td colspan="6" class="text-center text-danger">Error: ${err.message}</td></tr>`;
  }
}

async function loadProfile() {
  if (!getAuthToken()) {
    showView('login');
    return;
  }
  
  showView('profile');
  showLoading();
  
  try {
    const profile = await apiGet(CONFIG.api.endpoints.auth.profile);
    
    document.getElementById('profile-username').value = profile.username || '';
    document.getElementById('profile-email').value = profile.email || '';
    document.getElementById('profile-phone').value = profile.phoneNumber || '';
    document.getElementById('profile-role').value = profile.role || 'USER';
    
    document.getElementById('profile-detail-username').textContent = profile.username || 'Not set';
    document.getElementById('profile-detail-email').textContent = profile.email || 'Not provided';
    document.getElementById('profile-detail-phone').textContent = profile.phoneNumber || 'Not provided';
    document.getElementById('profile-detail-role').textContent = profile.role || 'USER';
    
    await loadProfileStats();
    
  } catch (err) {
    showAlert('profile-error', 'Failed to load profile: ' + err.message);
  } finally {
    hideLoading();
  }
}

async function loadProfileStats() {
  try {
    const balanceData = await apiGet(CONFIG.api.endpoints.account.balance);
    const balance = typeof balanceData.balance === 'number' ? balanceData.balance : 0;
    document.getElementById('profile-balance').textContent = `$${balance.toFixed(2)}`;
    
    try {
      const portfolio = await apiGet(CONFIG.api.endpoints.portfolio);
      const holdingsCount = Array.isArray(portfolio) ? portfolio.length : 0;
      document.getElementById('profile-holdings').textContent = holdingsCount;
    } catch (e) {
      document.getElementById('profile-holdings').textContent = '0';
    }
    
    try {
      const transactions = await apiGet(CONFIG.api.endpoints.transactions);
      const transactionsCount = Array.isArray(transactions) ? transactions.length : 0;
      document.getElementById('profile-transactions').textContent = transactionsCount;
    } catch (e) {
      document.getElementById('profile-transactions').textContent = '0';
    }
  } catch (err) {
  }
}

function showToast(title, message, type = 'info', duration = 5000) {
  const container = document.getElementById('toast-container');
  if (!container) {
    return;
  }
  
  const toast = document.createElement('div');
  toast.className = `toast ${type}`;
  toast.style.animation = 'slideInRight 0.3s ease-out';
  
  const icons = {
    success: 'fa-check-circle',
    error: 'fa-exclamation-circle',
    info: 'fa-info-circle',
    update: 'fa-sync-alt',
    warning: 'fa-exclamation-triangle'
  };
  
  const safeTitle = title.replace(/</g, '&lt;').replace(/>/g, '&gt;');
  const safeMessage = message.replace(/</g, '&lt;').replace(/>/g, '&gt;');
  
  toast.innerHTML = `
    <i class="fas ${icons[type] || icons.info}"></i>
    <div class="toast-content">
      <div class="toast-title">${safeTitle}</div>
      <div class="toast-message">${safeMessage}</div>
    </div>
    <button class="toast-close" onclick="this.parentElement.remove()" aria-label="Close">
      <i class="fas fa-times"></i>
    </button>
  `;
  
  container.appendChild(toast);
  
  const autoRemove = setTimeout(() => {
    toast.style.animation = 'toastSlideIn 0.3s ease-out reverse';
    setTimeout(() => {
      if (toast.parentElement) {
        toast.remove();
      }
    }, 300);
  }, duration);
  
  toast.addEventListener('mouseenter', () => {
    clearTimeout(autoRemove);
  });
  
  toast.addEventListener('mouseleave', () => {
    const newTimeout = setTimeout(() => {
      toast.style.animation = 'toastSlideIn 0.3s ease-out reverse';
      setTimeout(() => {
        if (toast.parentElement) {
          toast.remove();
        }
      }, 300);
    }, duration);
    toast.dataset.timeoutId = newTimeout;
  });
}

function addActivityFeedItem(symbol, price, prevPrice) {
  const feed = document.getElementById('activity-feed');
  if (!feed) return;
  
  const item = document.createElement('div');
  item.className = 'activity-item update';
  
  const change = prevPrice ? ((price - prevPrice) / prevPrice * 100).toFixed(2) : '0.00';
  const direction = price > prevPrice ? 'up' : price < prevPrice ? 'down' : 'unchanged';
  
  item.innerHTML = `
    <i class="fas fa-chart-line"></i>
    <span><strong>${symbol}</strong> updated: $${price.toFixed(2)} 
    ${prevPrice ? `(${direction === 'up' ? '+' : ''}${change}%)` : ''}</span>
  `;
  
  while (feed.children.length >= 10) {
    feed.removeChild(feed.firstChild);
  }
  
  feed.insertBefore(item, feed.firstChild);
  
  setTimeout(() => {
    item.style.opacity = '0.7';
  }, 5000);
}

function updateCounter() {
  updateCount++;
  const counter = document.getElementById('update-count');
  if (counter) {
    counter.textContent = updateCount;
    counter.style.animation = 'pulse 0.3s ease-out';
    setTimeout(() => {
      counter.style.animation = '';
    }, 300);
  }
}

function subscribeToPortfolioUpdates() {
  if (!stompClient || !stompClient.connected) {
    return;
  }
  
  if (portfolioData && portfolioData.length > 0) {
    portfolioData.forEach(asset => {
      const symbol = asset.symbol?.toUpperCase();
      if (symbol) {
        const topic = `${CONFIG.websocket.topicPrefix}/${symbol}`;
        
        stompClient.subscribe(topic, function(message) {
          try {
            const update = JSON.parse(message.body);
            const prevPrice = lastPrices.get(update.symbol);
            
            if (prevPrice !== update.price) {
              lastPrices.set(update.symbol, update.price);
              
              updatePriceInTable(update.symbol, update.price, prevPrice);
              updatePortfolioChart();
              addActivityFeedItem(update.symbol, update.price, prevPrice);
              updateCounter();
            }
          } catch (parseError) {
          }
        });
      }
    });
  }
}

function subscribeToUserNotifications() {
  if (!stompClient || !stompClient.connected) {
    return;
  }
  
  const username = localStorage.getItem(USERNAME_KEY);
  if (!username) {
    return;
  }
  
  const notificationTopic = `/topic/user/${username}/notifications`;
  
  stompClient.subscribe(notificationTopic, function(message) {
    try {
      const notification = JSON.parse(message.body);
      handleRealTimeNotification(notification);
    } catch (parseError) {
    }
  });
}

function handleRealTimeNotification(notification) {
  if (!notification || !notification.type) {
    return;
  }
  
  switch (notification.type) {
    case 'transaction':
      handleTransactionNotification(notification);
      break;
    case 'balance':
      handleBalanceNotification(notification);
      break;
    default:
      break;
  }
}

function handleTransactionNotification(notification) {
  const { transactionType, symbol, quantity, price, total } = notification;
  const message = `${transactionType} ${quantity} shares of ${symbol} at $${price.toFixed(2)} (Total: $${total.toFixed(2)})`;
  
  showToast(
    `${transactionType} Order Executed`,
    message,
    transactionType === 'BUY' ? 'success' : 'info',
    5000
  );
  
  if (window.location.hash === '#/portfolio' || window.location.hash === '#/transactions') {
    setTimeout(() => {
      if (window.location.hash === '#/portfolio') {
        loadPortfolio();
      }
      if (window.location.hash === '#/transactions') {
        loadTransactions();
      }
    }, 500);
  }
}

function handleBalanceNotification(notification) {
  const { balance, change } = notification;
  const changeText = change >= 0 ? `+$${change.toFixed(2)}` : `-$${Math.abs(change).toFixed(2)}`;
  
  const balanceElement = document.getElementById('balance-display');
  if (balanceElement) {
    balanceElement.textContent = `$${balance.toFixed(2)}`;
  }
  
  if (Math.abs(change) > 0.01) {
    showToast(
      'Balance Updated',
      `New balance: $${balance.toFixed(2)} (${changeText})`,
      change >= 0 ? 'success' : 'info',
      3000
    );
  }
}


function connectWebSocket() {
  if (!getAuthToken()) {
    return;
  }
  
  if (stompClient && stompClient.connected) {
    return;
  }

  const wsUrl = WS_BASE + CONFIG.websocket.endpoint;
  
  try {
    const socket = new SockJS(wsUrl);
    
    if (typeof Stomp !== 'undefined') {
      stompClient = Stomp.over(socket);
    } else {
      return;
    }

    const connectHeaders = {};
    const token = getAuthToken();
    if (token) {
      connectHeaders['Authorization'] = `Bearer ${token}`;
    }
    
    stompClient.connect(connectHeaders, function() {
      showToast('Connected', 'Real-time updates are now active', 'success', 3000);
      
      subscribeToPortfolioUpdates();
      
      subscribeToUserNotifications();
    }, function(error) {
      if (getAuthToken()) {
        showToast('Connection Lost', 'Attempting to reconnect...', 'error');
      }
      
      setTimeout(() => {
        if (getAuthToken() && (!stompClient || !stompClient.connected)) {
          connectWebSocket();
        }
      }, 5000);
    });
  } catch (error) {
    
    if (getAuthToken()) {
      showToast('Connection Failed', 'Could not establish WebSocket connection. Updates unavailable.', 'error');
    }
    
    if (getAuthToken()) {
      setTimeout(() => {
        if (!stompClient || !stompClient.connected) {
          connectWebSocket();
        }
      }, 10000);
    }
  }
}

function updatePriceInTable(symbol, price, prevPrice) {
  if (prevPrice !== null && prevPrice === price) {
    return;
  }
  
  if (!price || price <= 0 || isNaN(price)) {
    return;
  }
  
  const rows = document.querySelectorAll('#portfolio-table tbody tr');
  rows.forEach(row => {
    const cells = row.querySelectorAll('td');
    if (cells.length > 0 && cells[0].textContent.trim() === symbol) {
      requestAnimationFrame(() => {
        if (cells[3]) {
          const priceCell = cells[3];
          const currentPriceText = `$${price.toFixed(2)}`;
          
          if (priceCell.textContent !== currentPriceText) {
            const direction = prevPrice ? (price > prevPrice ? 'up' : price < prevPrice ? 'down' : '') : '';
            
            priceCell.textContent = currentPriceText;
            priceCell.classList.add('price-update');
            
            if (direction === 'up') {
              priceCell.classList.add('price-up');
              setTimeout(() => priceCell.classList.remove('price-up'), 500);
            } else if (direction === 'down') {
              priceCell.classList.add('price-down');
              setTimeout(() => priceCell.classList.remove('price-down'), 500);
            }
            
            setTimeout(() => priceCell.classList.remove('price-update'), 500);
          }
        }
        
        if (cells[4]) {
          const qty = parseFloat(cells[1].textContent) || 0;
          const marketValue = qty * price;
          const valueText = `$${marketValue.toFixed(2)}`;
          if (cells[4].textContent !== valueText) {
            cells[4].textContent = valueText;
          }
        }
        
        if (cells[5] && cells[6]) {
          const qty = parseFloat(cells[1].textContent) || 0;
          const buyPrice = parseFloat(cells[2].textContent.replace('$', '').replace(',', '')) || 0;
          const pl = (price - buyPrice) * qty;
          const plPercent = buyPrice > 0 ? ((pl / (qty * buyPrice)) * 100) : 0;
          
          const plText = `${pl >= 0 ? '+' : ''}$${pl.toFixed(2)}`;
          const plPercentText = `${plPercent >= 0 ? '+' : ''}${plPercent.toFixed(2)}%`;
          const plClass = pl >= 0 ? 'pl-positive' : 'pl-negative';
          
          if (cells[5].textContent !== plText) {
            cells[5].textContent = plText;
            cells[5].className = plClass;
          }
          
          if (cells[6].textContent !== plPercentText) {
            cells[6].textContent = plPercentText;
            cells[6].className = plClass;
          }
        }
      });
    }
  });
}

function createPortfolioChart() {
  const canvas = document.getElementById('portfolio-chart');
  if (!canvas) return;

  const ctx = canvas.getContext('2d');
  
  const colorPalette = [
    'rgba(59, 130, 246, 0.8)',
    'rgba(16, 185, 129, 0.8)',
    'rgba(239, 68, 68, 0.8)',
    'rgba(245, 158, 11, 0.8)',
    'rgba(139, 92, 246, 0.8)',
    'rgba(236, 72, 153, 0.8)',
    'rgba(34, 197, 94, 0.8)',
    'rgba(251, 191, 36, 0.8)',
    'rgba(99, 102, 241, 0.8)',
    'rgba(249, 115, 22, 0.8)',
    'rgba(14, 165, 233, 0.8)',
    'rgba(168, 85, 247, 0.8)'
  ];
  
  priceChart = new Chart(ctx, {
    type: 'doughnut',
    data: {
      labels: [],
      datasets: [{
        label: 'Portfolio Value',
        data: [],
        backgroundColor: colorPalette,
        borderColor: 'rgba(255, 255, 255, 0.1)',
        borderWidth: 2,
        hoverOffset: 4
      }]
    },
    options: {
      responsive: true,
      maintainAspectRatio: true,
      aspectRatio: 1.5,
      animation: {
        duration: 800,
        animateRotate: true,
        animateScale: true
      },
      plugins: {
        legend: {
          position: 'bottom',
          labels: {
            color: '#f8fafc',
            padding: 15,
            font: {
              size: 12
            },
            generateLabels: function(chart) {
              const data = chart.data;
              if (data.labels.length && data.datasets.length) {
                const dataset = data.datasets[0];
                const total = dataset.data.reduce((a, b) => a + b, 0);
                return data.labels.map((label, i) => {
                  const value = dataset.data[i];
                  const percentage = total > 0 ? ((value / total) * 100).toFixed(1) : 0;
                  return {
                    text: `${label}: $${value.toFixed(2)} (${percentage}%)`,
                    fillStyle: dataset.backgroundColor[i],
                    strokeStyle: dataset.borderColor,
                    lineWidth: dataset.borderWidth,
                    hidden: false,
                    index: i
                  };
                });
              }
              return [];
            }
          }
        },
        tooltip: {
          backgroundColor: 'rgba(0, 0, 0, 0.8)',
          titleColor: '#f8fafc',
          bodyColor: '#f8fafc',
          borderColor: 'rgba(255, 255, 255, 0.1)',
          borderWidth: 1,
          padding: 12,
          callbacks: {
            label: function(context) {
              const label = context.label || '';
              const value = context.parsed || 0;
              const total = context.dataset.data.reduce((a, b) => a + b, 0);
              const percentage = total > 0 ? ((value / total) * 100).toFixed(2) : 0;
              return `${label}: $${value.toFixed(2)} (${percentage}%)`;
            }
          }
        },
        title: {
          display: false
        }
      }
    }
  });
}

function updatePortfolioChart() {
  if (!priceChart) {
    createPortfolioChart();
    return;
  }
  
  if (chartUpdateTimeout) {
    clearTimeout(chartUpdateTimeout);
  }
  
  chartUpdateTimeout = setTimeout(() => {
    const rows = document.querySelectorAll('#portfolio-table tbody tr');
    const allocationData = [];
    
    rows.forEach(row => {
      const cells = row.querySelectorAll('td');
      if (cells.length > 0) {
        const symbol = cells[0].textContent.trim();
        const valueText = cells[4].textContent.replace(/[^0-9.-]/g, '');
        const value = parseFloat(valueText) || 0;
        if (value > 0 && symbol && symbol !== 'Loading...' && symbol !== 'No assets in portfolio') {
          allocationData.push({ symbol, value });
        }
      }
    });
    
    allocationData.sort((a, b) => b.value - a.value);
    
    const labels = allocationData.map(item => item.symbol);
    const values = allocationData.map(item => item.value);
    const totalValue = values.reduce((sum, val) => sum + val, 0);
    
    const summaryDiv = document.getElementById('allocation-summary');
    if (summaryDiv) {
      if (allocationData.length > 0) {
        summaryDiv.style.display = 'block';
        document.getElementById('total-holdings').textContent = allocationData.length;
        document.getElementById('total-allocation-value').textContent = `$${totalValue.toFixed(2)}`;
        document.getElementById('largest-position').textContent = 
          allocationData.length > 0 ? `${allocationData[0].symbol} (${((allocationData[0].value / totalValue) * 100).toFixed(1)}%)` : '-';
      } else {
        summaryDiv.style.display = 'none';
      }
    }
    
    const currentLabels = priceChart.data.labels.join(',');
    const newLabels = labels.join(',');
    const currentData = priceChart.data.datasets[0].data.join(',');
    const newData = values.join(',');
    
    if (currentLabels !== newLabels || currentData !== newData) {
      priceChart.data.labels = labels;
      priceChart.data.datasets[0].data = values;
      
      if (priceChart.data.datasets[0].backgroundColor.length < labels.length) {
        const colorPalette = [
          'rgba(59, 130, 246, 0.8)', 'rgba(16, 185, 129, 0.8)', 'rgba(239, 68, 68, 0.8)',
          'rgba(245, 158, 11, 0.8)', 'rgba(139, 92, 246, 0.8)', 'rgba(236, 72, 153, 0.8)',
          'rgba(34, 197, 94, 0.8)', 'rgba(251, 191, 36, 0.8)', 'rgba(99, 102, 241, 0.8)',
          'rgba(249, 115, 22, 0.8)', 'rgba(14, 165, 233, 0.8)', 'rgba(168, 85, 247, 0.8)'
        ];
        const extendedColors = [];
        for (let i = 0; i < labels.length; i++) {
          extendedColors.push(colorPalette[i % colorPalette.length]);
        }
        priceChart.data.datasets[0].backgroundColor = extendedColors;
      }
      
      priceChart.update('active');
    }
  }, 300);
}

function setupSearchBox(searchBoxId, searchBtnId, resultsId, targetInputId) {
  const searchBox = document.getElementById(searchBoxId);
  const searchBtn = document.getElementById(searchBtnId);
  const results = document.getElementById(resultsId);
  
  if (!searchBox || !results) return;
  
  let searchTimeout;
  
  const performSearch = async (query) => {
    const trimmedQuery = query.trim();
    
    if (trimmedQuery.length < 1) {
      results.innerHTML = '';
      results.style.display = 'none';
      return;
    }
    
    results.innerHTML = '<div class="search-loading-state"><i class="fas fa-spinner fa-spin"></i><p>Searching...</p></div>';
    results.style.display = 'block';
    
    try {
      let items = [];
      try {
        const headers = { 'Content-Type': 'application/json' };
        const token = getAuthToken();
        if (token) {
          headers['Authorization'] = `Bearer ${token}`;
        }
        
        const res = await fetch(`${API_BASE}${CONFIG.api.endpoints.search}?q=${encodeURIComponent(trimmedQuery)}`, {
          method: 'GET',
          headers: headers,
          mode: 'cors',
          credentials: 'include'
        });
        
        if (res.ok) {
          items = await res.json();
          if (!Array.isArray(items)) {
            items = [];
          }
        } else {
          throw new Error('Backend search failed');
        }
      } catch (backendError) {
        try {
          const yahooRes = await fetch(`https://query1.finance.yahoo.com/v1/finance/search?q=${encodeURIComponent(trimmedQuery)}`);
          if (yahooRes.ok) {
            const yahooData = await yahooRes.json();
            items = (yahooData.quotes || []).slice(0, 10).map(q => ({
              symbol: q.symbol || '',
              name: q.shortname || q.longname || q.symbol || 'Unknown'
            })).filter(q => q.symbol);
          }
        } catch (yahooError) {
        }
      }
      
      if (items.length === 0) {
        results.innerHTML = '<div class="search-empty-state"><i class="fas fa-search"></i><p>No results found. Try a different search term.</p></div>';
        results.style.display = 'block';
        return;
      }
      
      results.innerHTML = `<ul class="search-results-list">${items.map(item => 
        `<li class="search-result-item" data-sym="${item.symbol || ''}">
          <div class="result-symbol">${item.symbol || 'N/A'}</div>
          <div class="result-name">${item.name || ''}</div>
        </li>`
      ).join('')}</ul>`;
      results.style.display = 'block';
      
      results.querySelectorAll('li.search-result-item').forEach(li => {
        li.addEventListener('click', function() {
          const symbol = this.dataset.sym;
          if (symbol) {
            const targetInput = document.getElementById(targetInputId);
            if (targetInput) {
              targetInput.value = symbol;
              targetInput.focus();
            }
            results.innerHTML = '';
            results.style.display = 'none';
            searchBox.value = symbol;
            showNotification('Symbol Selected', `Selected ${symbol}. You can now enter quantity and price to trade.`, 'success');
          }
        });
      });
    } catch (err) {
      results.innerHTML = `<div class="text-center" style="padding:1rem;color:#f44336;">Search error: ${err.message || 'Unable to search stocks. Please try again.'}</div>`;
      results.style.display = 'block';
    }
  };
  
  searchBox.addEventListener('input', function() {
    clearTimeout(searchTimeout);
    const query = this.value.trim();
    
    if (query.length < 1) {
      results.innerHTML = '';
      results.style.display = 'none';
      return;
    }
    
    searchTimeout = setTimeout(() => {
      performSearch(query);
    }, 500);
  });
  
  searchBox.addEventListener('keypress', function(e) {
    if (e.key === 'Enter') {
      e.preventDefault();
      clearTimeout(searchTimeout);
      performSearch(this.value);
    }
  });
  
  if (searchBtn) {
    searchBtn.addEventListener('click', function() {
      clearTimeout(searchTimeout);
      performSearch(searchBox.value);
    });
  }
}

function initStockSearch() {
  setupSearchBox('stock-search-box', 'stock-search-btn', 'stock-search-results', 'trade-symbol');
  
  setupSearchBox('add-asset-search-box', 'add-asset-search-btn', 'add-asset-search-results', 'add-asset-symbol');
}

function initForms() {
  
  const loginForm = document.getElementById('form-login');
  if (loginForm) {
    loginForm.addEventListener('submit', async (e) => {
      e.preventDefault();
      const username = document.getElementById('login-username').value;
      const password = document.getElementById('login-password').value;
      
      try {
        showLoading();
        const data = await apiPost(CONFIG.api.endpoints.auth.login, { username, password });
        setAuth(data.token, data.username);
        showView('portfolio');
      } catch (err) {
        let errorMessage = err.message || 'Login failed';
        showAlert('login-error', errorMessage);
      } finally {
        hideLoading();
      }
    });
  }
  
  let registrationEmail = null;
  let otpExpiryTimer = null;
  
  const registerForm = document.getElementById('form-register');
  if (registerForm) {
    registerForm.addEventListener('submit', async (e) => {
      e.preventDefault();
      const username = document.getElementById('register-username').value.trim();
      const email = document.getElementById('register-email').value.trim();
      const phoneNumber = document.getElementById('register-phone').value.trim();
      
      if (!username || username.length < 3) {
        showNotification('Validation Error', 'Username must be at least 3 characters long', 'error');
        showAlert('register-error', 'Username must be at least 3 characters long');
        return;
      }
      
      if (!email) {
        showNotification('Validation Error', 'Email is required', 'error');
        showAlert('register-error', 'Email is required');
        return;
      }
      
      if (!phoneNumber) {
        showNotification('Validation Error', 'Phone number is required', 'error');
        showAlert('register-error', 'Phone number is required');
        return;
      }
      
      if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
        showNotification('Validation Error', 'Please enter a valid email address', 'error');
        showAlert('register-error', 'Please enter a valid email address');
        return;
      }
      
      try {
        showLoading();
        const requestBody = { 
          username,
          email: email,
          phoneNumber: phoneNumber
        };
        const data = await apiPost(CONFIG.api.endpoints.auth.register, requestBody);
        
        registrationEmail = email;
        
        registerForm.style.display = 'none';
        const otpForm = document.getElementById('form-verify-otp');
        if (otpForm) {
          otpForm.style.display = 'block';
          const emailDisplay = document.getElementById('otp-email-display');
          if (emailDisplay) {
            emailDisplay.textContent = email;
          }
          const otpInput = document.getElementById('register-otp');
          if (otpInput) {
            otpInput.focus();
          }
        }
        
        if (data.expiresInSeconds) {
          startOtpExpiryTimer(data.expiresInSeconds);
        }
        
        showNotification('OTP Sent', data.message || 'OTP sent successfully! Please check your email.', 'success');
        
        const errorAlert = document.getElementById('register-error');
        if (errorAlert) {
          errorAlert.style.display = 'none';
        }
      } catch (err) {
        showNotification('Error', err.message || 'Failed to send OTP', 'error');
        showAlert('register-error', err.message || 'Failed to send OTP');
      } finally {
        hideLoading();
      }
    });
  }
  
  const otpForm = document.getElementById('form-verify-otp');
  if (otpForm) {
    otpForm.addEventListener('submit', async (e) => {
      e.preventDefault();
      const otp = document.getElementById('register-otp').value.trim();
      
      if (!otp || otp.length !== 6 || !/^\d{6}$/.test(otp)) {
        showNotification('Validation Error', 'Please enter a valid 6-digit OTP', 'error');
        showAlert('register-error', 'Please enter a valid 6-digit OTP');
        return;
      }
      
      if (!registrationEmail) {
        showNotification('Error', 'Registration session expired. Please start again.', 'error');
        showAlert('register-error', 'Registration session expired. Please start again.');
        otpForm.style.display = 'none';
        registerForm.style.display = 'block';
        return;
      }
      
      try {
        showLoading();
        const data = await apiPost(CONFIG.api.endpoints.auth.verifyOtp, {
          email: registrationEmail,
          otp: otp
        });
        
        stopOtpExpiryTimer();
        
        showNotification('Success!', 'OTP verified successfully! Please set your password.', 'success');
        
        otpForm.style.display = 'none';
        const passwordForm = document.getElementById('form-set-password');
        if (passwordForm) {
          passwordForm.style.display = 'block';
          const passwordInput = document.getElementById('register-password');
          if (passwordInput) {
            passwordInput.focus();
          }
        }
        
        const errorAlert = document.getElementById('register-error');
        if (errorAlert) {
          errorAlert.style.display = 'none';
        }
      } catch (err) {
        showNotification('Error', err.message || 'OTP verification failed', 'error');
        showAlert('register-error', err.message || 'OTP verification failed');
        document.getElementById('register-otp').value = '';
      } finally {
        hideLoading();
      }
    });
  }
  
  const passwordForm = document.getElementById('form-set-password');
  if (passwordForm) {
    passwordForm.addEventListener('submit', async (e) => {
      e.preventDefault();
      const password = document.getElementById('register-password').value;
      const passwordConfirm = document.getElementById('register-password-confirm').value;
      
      if (!password || password.length < 6) {
        showNotification('Validation Error', 'Password must be at least 6 characters long', 'error');
        showAlert('register-error', 'Password must be at least 6 characters long');
        return;
      }
      
      if (password !== passwordConfirm) {
        showNotification('Validation Error', 'Passwords do not match', 'error');
        showAlert('register-error', 'Passwords do not match');
        return;
      }
      
      if (!registrationEmail) {
        showNotification('Error', 'Registration session expired. Please start again.', 'error');
        showAlert('register-error', 'Registration session expired. Please start again.');
        passwordForm.style.display = 'none';
        registerForm.style.display = 'block';
        return;
      }
      
      try {
        showLoading();
        const data = await apiPost(CONFIG.api.endpoints.auth.completeRegistration, {
          email: registrationEmail,
          password: password
        });
        
        setAuth(data.token, data.username);
        showNotification('Success!', 'Account created successfully! Welcome to TradeShift!', 'success');
        
        registerForm.reset();
        otpForm.reset();
        passwordForm.reset();
        registerForm.style.display = 'block';
        otpForm.style.display = 'none';
        passwordForm.style.display = 'none';
        registrationEmail = null;
        
        setTimeout(() => showView('portfolio'), 1000);
      } catch (err) {
        showNotification('Error', err.message || 'Registration failed', 'error');
        showAlert('register-error', err.message || 'Registration failed');
      } finally {
        hideLoading();
      }
    });
  }
  
  const backToOtpBtn = document.getElementById('back-to-otp-btn');
  if (backToOtpBtn) {
    backToOtpBtn.addEventListener('click', () => {
      const passwordForm = document.getElementById('form-set-password');
      if (passwordForm) {
        passwordForm.style.display = 'none';
        passwordForm.reset();
      }
      const otpForm = document.getElementById('form-verify-otp');
      if (otpForm) {
        otpForm.style.display = 'block';
        document.getElementById('register-otp').focus();
      }
      
      const errorAlert = document.getElementById('register-error');
      if (errorAlert) {
        errorAlert.style.display = 'none';
      }
    });
  }
  
  const backToRegisterBtn = document.getElementById('back-to-register-btn');
  if (backToRegisterBtn) {
    backToRegisterBtn.addEventListener('click', () => {
      const otpForm = document.getElementById('form-verify-otp');
      if (otpForm) {
        otpForm.style.display = 'none';
        otpForm.reset();
      }
      registerForm.style.display = 'block';
      registrationEmail = null;
      
      stopOtpExpiryTimer();
      
      const errorAlert = document.getElementById('register-error');
      if (errorAlert) {
        errorAlert.style.display = 'none';
      }
    });
  }
  
  function startOtpExpiryTimer(seconds) {
    stopOtpExpiryTimer();
    
    const timerElement = document.getElementById('otp-expiry-timer');
    if (!timerElement) return;
    
    let remaining = seconds;
    
    const updateTimer = () => {
      const minutes = Math.floor(remaining / 60);
      const secs = remaining % 60;
      
      if (remaining > 0) {
        timerElement.textContent = `OTP expires in: ${minutes}:${secs.toString().padStart(2, '0')}`;
        timerElement.style.color = remaining <= 60 ? '#ef4444' : '#fbbf24';
        remaining--;
      } else {
        timerElement.textContent = 'OTP has expired. Please request a new one.';
        timerElement.style.color = '#ef4444';
        stopOtpExpiryTimer();
      }
    };
    
    updateTimer();
    otpExpiryTimer = setInterval(updateTimer, 1000);
  }
  
  function stopOtpExpiryTimer() {
    if (otpExpiryTimer) {
      clearInterval(otpExpiryTimer);
      otpExpiryTimer = null;
    }
    const timerElement = document.getElementById('otp-expiry-timer');
    if (timerElement) {
      timerElement.textContent = '';
    }
  }
  
  
  
  const creditForm = document.getElementById('credit-form');
  if (creditForm) {
    creditForm.addEventListener('submit', async (e) => {
      e.preventDefault();
      const amountInput = document.getElementById('credit-amount');
      const amount = parseFloat(amountInput.value);
      
      if (isNaN(amount) || amount <= 0) {
        showNotification('Validation Error', 'Please enter a valid amount (must be greater than 0)', 'error');
        amountInput.focus();
        return;
      }
      
      try {
        showLoading();
        await apiPost(CONFIG.api.endpoints.account.credit, { amount });
        amountInput.value = '';
        fetchBalance();
        fetchPortfolioData();
        showNotification('Success!', `Successfully added $${amount.toFixed(2)} to your account!`, 'success');
      } catch (err) {
        showNotification('Error', 'Failed to add funds: ' + err.message, 'error');
      } finally {
        hideLoading();
      }
    });
  }
  
  const tradeForm = document.getElementById('trade-form');
  if (tradeForm) {
    tradeForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    const symbol = document.getElementById('trade-symbol').value.trim().toUpperCase();
    const quantity = parseFloat(document.getElementById('trade-qty').value);
    const price = parseFloat(document.getElementById('trade-price').value);
      
      if (!symbol || !quantity || !price || quantity <= 0 || price <= 0) {
        showNotification('Validation Error', 'Please fill in all fields with valid values', 'error');
        return;
      }

    try {
        showLoading();
        await apiPost(CONFIG.api.endpoints.trades.buy, { symbol, quantity, price });
        tradeForm.reset();
      fetchBalance();
        fetchPortfolioData();
      showNotification('Success!', `Buy order executed successfully! Purchased ${quantity} shares of ${symbol} at $${price.toFixed(2)} per share.`, 'success');
    } catch (err) {
      showNotification('Error', 'Buy order failed: ' + err.message, 'error');
      } finally {
        hideLoading();
      }
    });
    }

  const sellBtn = document.getElementById('sell-btn');
  if (sellBtn) {
    sellBtn.onclick = async () => {
      const symbol = document.getElementById('trade-symbol').value.trim().toUpperCase();
      const quantity = parseFloat(document.getElementById('trade-qty').value);
      const price = parseFloat(document.getElementById('trade-price').value);

      if (!symbol || !quantity || !price || quantity <= 0 || price <= 0) {
        showNotification('Validation Error', 'Please fill in all fields with valid values', 'error');
        return;
      }

      const confirmed = confirm(`Are you sure you want to sell ${quantity} shares of ${symbol} at $${price.toFixed(2)} per share?\n\nTotal proceeds: $${(quantity * price).toFixed(2)}`);
      if (!confirmed) return;

      try {
        showLoading();
        await apiPost(CONFIG.api.endpoints.trades.sell, { symbol, quantity, price });
        document.getElementById('trade-form').reset();
        fetchBalance();
        fetchPortfolioData();
        showNotification('Success!', `Sell order executed successfully! Sold ${quantity} shares of ${symbol} at $${price.toFixed(2)} per share.`, 'success');
      } catch (err) {
        const errorMessage = err.message || 'Sell order failed';
        showNotification('Error', 'Sell order failed: ' + errorMessage, 'error');
      } finally {
        hideLoading();
      }
    };
  }
  
  const addAssetForm = document.getElementById('add-asset-form');
  if (addAssetForm) {
    addAssetForm.addEventListener('submit', async (e) => {
      e.preventDefault();
      const symbolInput = document.getElementById('add-asset-symbol');
      const quantityInput = document.getElementById('add-asset-qty');
      const priceInput = document.getElementById('add-asset-price');
      
      const symbol = symbolInput.value.trim().toUpperCase();
      const quantity = parseFloat(quantityInput.value);
      const avgBuyPrice = parseFloat(priceInput.value);
      
      if (!symbol || symbol.length === 0) {
        showNotification('Validation Error', 'Please enter a valid stock symbol', 'error');
        symbolInput.focus();
        return;
      }
      
      if (isNaN(quantity) || quantity <= 0) {
        showNotification('Validation Error', 'Please enter a valid quantity (must be greater than 0)', 'error');
        quantityInput.focus();
        return;
      }
      
      if (isNaN(avgBuyPrice) || avgBuyPrice <= 0) {
        showNotification('Validation Error', 'Please enter a valid buy price (must be greater than 0)', 'error');
        priceInput.focus();
        return;
      }
      
      try {
        showLoading();
        const response = await apiPost(CONFIG.api.endpoints.portfolio, { 
          symbol, 
          quantity, 
          avgBuyPrice 
        });
        
        addAssetForm.reset();
        fetchPortfolioData();
        fetchBalance();
        showNotification('Success!', `Successfully added ${quantity} shares of ${symbol} at $${avgBuyPrice.toFixed(2)} per share!`, 'success');
      } catch (err) {
        const errorMessage = err.message || 'Failed to add asset';
        showNotification('Error', 'Failed to add asset: ' + errorMessage, 'error');
      } finally {
        hideLoading();
      }
    });
  }
  
  const refreshBtn = document.getElementById('refresh-balance');
  if (refreshBtn) {
    refreshBtn.onclick = fetchBalance;
  }
  
  const logoutBtn = document.getElementById('nav-logout');
  if (logoutBtn) {
    logoutBtn.onclick = () => {
        const confirmed = confirm('Are you sure you want to logout?');
        if (confirmed) {
          showNotification('Logged Out', 'You have been successfully logged out. Thank you for using TradeShift!', 'info');
          setTimeout(() => {
            clearAuth();
          }, 1000);
        }
    };
  }
  
  const profileForm = document.getElementById('profile-form');
  if (profileForm) {
    profileForm.addEventListener('submit', async (e) => {
      e.preventDefault();
      const email = document.getElementById('profile-email').value.trim();
      const phoneNumber = document.getElementById('profile-phone').value.trim();
      
      if (!email && !phoneNumber) {
        showNotification('Validation Error', 'Please provide either email or phone number', 'error');
        showAlert('profile-error', 'Either email or phone number must be provided');
        return;
      }
      
      if (email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
        showNotification('Validation Error', 'Please enter a valid email address', 'error');
        showAlert('profile-error', 'Please enter a valid email address');
        return;
      }
      
      try {
        showLoading();
        const requestBody = {};
        
        if (email) {
          requestBody.email = email;
        } else if (email === '') {
          requestBody.email = null;
        }
        
        if (phoneNumber) {
          requestBody.phoneNumber = phoneNumber;
        } else if (phoneNumber === '') {
          requestBody.phoneNumber = null;
        }
        
        const data = await apiPut(CONFIG.api.endpoints.auth.profile, requestBody);
        
        const errorAlert = document.getElementById('profile-error');
        if (errorAlert) {
          errorAlert.style.display = 'none';
        }
        
        showNotification('Success!', 'Profile updated successfully!', 'success');
        showAlert('profile-success', 'Profile updated successfully!');
        
        document.getElementById('profile-detail-email').textContent = data.email || 'Not provided';
        document.getElementById('profile-detail-phone').textContent = data.phoneNumber || 'Not provided';
        
        document.getElementById('profile-email').value = data.email || '';
        document.getElementById('profile-phone').value = data.phoneNumber || '';
        
        setTimeout(() => {
          const successAlert = document.getElementById('profile-success');
          if (successAlert) {
            successAlert.style.display = 'none';
          }
        }, 5000);
      } catch (err) {
        showNotification('Error', err.message || 'Failed to update profile', 'error');
        showAlert('profile-error', err.message || 'Failed to update profile');
      } finally {
        hideLoading();
      }
    });
  }
}

function handleRoute() {
  const hash = location.hash.replace('#/', '');
  
  if (!hash || hash === 'login') {
    showView('login');
  } else if (hash === 'register') {
    showView('register');
  } else if (hash === 'portfolio') {
    if (getAuthToken()) {
      showView('portfolio');
    } else {
      showView('login');
    }
  } else if (hash === 'transactions') {
    if (getAuthToken()) {
      showView('transactions');
    } else {
      showView('login');
    }
  } else if (hash === 'profile') {
    if (getAuthToken()) {
      showView('profile');
    } else {
      showView('login');
    }
  } else {
    showView('login');
  }
}

function createRipple(event) {
  const button = event.currentTarget;
  const circle = document.createElement('span');
  const diameter = Math.max(button.clientWidth, button.clientHeight);
  const radius = diameter / 2;

  circle.style.width = circle.style.height = `${diameter}px`;
  circle.style.left = `${event.clientX - button.offsetLeft - radius}px`;
  circle.style.top = `${event.clientY - button.offsetTop - radius}px`;
  circle.classList.add('ripple');

  const ripple = button.getElementsByClassName('ripple')[0];
  if (ripple) {
    ripple.remove();
  }

  button.appendChild(circle);
}

function initClickEffects() {
  document.querySelectorAll('.btn').forEach(btn => {
    btn.addEventListener('click', createRipple);
    btn.classList.add('clickable');
  });

  document.querySelectorAll('.nav-link').forEach(link => {
    link.classList.add('clickable');
  });

  document.querySelectorAll('.data-table tbody tr').forEach(row => {
    row.classList.add('clickable');
    row.addEventListener('click', function(e) {
      if (!e.target.closest('button')) {
        this.style.transform = 'scale(0.98)';
        setTimeout(() => {
          this.style.transform = '';
        }, 150);
      }
    });
  });

  document.querySelectorAll('.card').forEach(card => {
    card.style.cursor = 'default';
  });
}

window.addEventListener('DOMContentLoaded', async function() {
  if (isFileProtocol) {
    const loginView = document.getElementById('view-login');
    if (loginView && loginView.style.display !== 'none') {
      const errorDiv = document.getElementById('login-error');
      if (errorDiv) {
        errorDiv.textContent = 'Warning: Running from file:// protocol. Please serve the frontend using a web server. Run "npm start" from the frontend directory.';
        errorDiv.className = 'alert alert-error';
        errorDiv.style.display = 'block';
      }
    }
  }
  
  if (typeof CONFIG === 'undefined' || !CONFIG.api) {
    await new Promise(resolve => setTimeout(resolve, 100));
    if (typeof CONFIG !== 'undefined' && CONFIG.api && CONFIG.api.baseUrl) {
      API_BASE = CONFIG.api.baseUrl;
    }
  }
  
  initForms();
  initPortfolioTabs();
  initStockSearch();
  
  initClickEffects();
  
  window.addEventListener('hashchange', handleRoute);
  
  if (!location.hash || location.hash === '#/' || location.hash === '#/login') {
    location.hash = '#/login';
  }
  
  handleRoute();
  updateNav();
  
  if (getAuthToken()) {
    currentUser = localStorage.getItem(USERNAME_KEY);
    updateNav();
  }

  const observer = new MutationObserver(function(mutations) {
    initClickEffects();
  });
  
  observer.observe(document.body, {
    childList: true,
    subtree: true
  });
});

function initPortfolioTabs() {
  const tabs = document.querySelectorAll('.portfolio-tab');
  const tabContents = document.querySelectorAll('.portfolio-tab-content');
  
  tabs.forEach(tab => {
    tab.addEventListener('click', () => {
      const targetTab = tab.dataset.tab;
      
      tabs.forEach(t => t.classList.remove('active'));
      tabContents.forEach(content => content.classList.remove('active'));
      
      tab.classList.add('active');
      const targetContent = document.getElementById(`portfolio-tab-${targetTab}`);
      if (targetContent) {
        targetContent.classList.add('active');
      }
    });
  });
}

