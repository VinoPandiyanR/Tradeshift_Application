var CONFIG = {
  api: {
    baseUrl: window.location.hostname === '127.0.0.1' || window.location.hostname === 'localhost' 
      ? `http://${window.location.hostname}:8080` 
      : 'http://localhost:8080',
    endpoints: {
      auth: {
        login: '/api/auth/login',
        register: '/api/auth/register',
        verifyOtp: '/api/auth/verify-otp',
        completeRegistration: '/api/auth/complete-registration',
        profile: '/api/auth/profile'
      },
      account: {
        balance: '/api/account/balance',
        credit: '/api/account/credit'
      },
      portfolio: '/api/portfolio',
      trades: {
        buy: '/api/trades/buy',
        sell: '/api/trades/sell'
      },
      transactions: '/api/transactions',
      analytics: '/api/analytics/summary',
      search: '/api/search/stock'
    }
  },
  websocket: {
    endpoint: '/ws/prices',
    topicPrefix: '/topic/prices'
  },
  ui: {
    updateInterval: 5000,
    theme: 'dark'
  }
};

(function() {
  try {
    fetch('config.json')
      .then(res => res.ok ? res.json() : null)
      .then(json => {
        if (json) {
          if (json.api) {
            CONFIG.api = { ...CONFIG.api, ...json.api };
            if (json.api.endpoints) {
              CONFIG.api.endpoints = { ...CONFIG.api.endpoints, ...json.api.endpoints };
            }
          }
          if (json.websocket) {
            CONFIG.websocket = { ...CONFIG.websocket, ...json.websocket };
          }
          if (json.ui) {
            CONFIG.ui = { ...CONFIG.ui, ...json.ui };
          }
          console.log('Configuration loaded from config.json');
        }
      })
      .catch(() => {
        console.log('Using default configuration (config.json not found)');
      });
  } catch (e) {
    console.warn('Error loading config.json:', e);
  }
})();

