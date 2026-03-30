const orderForm = document.getElementById("order-form");
const orderStatus = document.getElementById("order-status");
const lookupOrderId = document.getElementById("lookupOrderId");
const lookupAccountId = document.getElementById("lookupAccountId");
const lookupSymbol = document.getElementById("lookupSymbol");
const orderResult = document.getElementById("order-result");
const balanceResult = document.getElementById("balance-result");
const positionsResult = document.getElementById("positions-result");
const orderbookResult = document.getElementById("orderbook-result");
const tradesResult = document.getElementById("trades-result");

document.getElementById("seed-demo").addEventListener("click", () => {
  document.getElementById("accountId").value = "ACC-001";
  document.getElementById("clientOrderId").value = `web-${Date.now()}`;
  document.getElementById("symbol").value = "AAPL";
  document.getElementById("side").value = "BUY";
  document.getElementById("price").value = "100";
  document.getElementById("quantity").value = "1";
});

document.getElementById("refresh-all").addEventListener("click", refreshAll);

orderForm.addEventListener("submit", async (event) => {
  event.preventDefault();

  const payload = {
    accountId: document.getElementById("accountId").value.trim(),
    clientOrderId: document.getElementById("clientOrderId").value.trim(),
    symbol: document.getElementById("symbol").value.trim().toUpperCase(),
    side: document.getElementById("side").value,
    price: Number(document.getElementById("price").value),
    quantity: Number(document.getElementById("quantity").value),
  };

  setStatus("Submitting order…", "muted");

  try {
    const response = await fetchJson("/orders", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    });

    lookupOrderId.value = response.orderId;
    lookupAccountId.value = response.accountId;
    lookupSymbol.value = response.symbol;

    setStatus(`Order ${response.orderId} created with status ${response.status}.`, "success");
    renderJson(orderResult, response);

    await wait(500);
    await refreshAll();
  } catch (error) {
    setStatus(error.message, "error");
  }
});

async function refreshAll() {
  const tasks = [];
  const orderId = lookupOrderId.value.trim();
  const accountId = lookupAccountId.value.trim();
  const symbol = lookupSymbol.value.trim().toUpperCase();

  if (orderId) {
    tasks.push(loadInto(`/orders/${orderId}`, orderResult));
  }
  if (accountId) {
    tasks.push(loadInto(`/accounts/${accountId}/balances`, balanceResult));
    tasks.push(loadInto(`/accounts/${accountId}/positions`, positionsResult));
  }
  if (symbol) {
    tasks.push(loadInto(`/market-data/${symbol}/orderbook`, orderbookResult));
    tasks.push(loadInto(`/market-data/${symbol}/trades`, tradesResult));
  }

  if (tasks.length === 0) {
    setStatus("Add an order, account, or symbol to refresh state.", "muted");
    return;
  }

  try {
    await Promise.all(tasks);
    setStatus("State refreshed.", "muted");
  } catch (error) {
    setStatus(error.message, "error");
  }
}

async function loadInto(url, element) {
  const result = await fetchJson(url);
  renderJson(element, result);
}

async function fetchJson(url, options = {}) {
  const response = await fetch(url, options);
  const text = await response.text();
  const body = text ? JSON.parse(text) : null;

  if (!response.ok) {
    const message = body?.message || `Request failed: ${response.status}`;
    throw new Error(message);
  }

  return body;
}

function renderJson(element, value) {
  element.textContent = JSON.stringify(value, null, 2);
}

function setStatus(message, tone) {
  orderStatus.textContent = message;
  orderStatus.className = `status-card ${tone}`;
}

function wait(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

document.getElementById("seed-demo").click();
