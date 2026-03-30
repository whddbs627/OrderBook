#!/usr/bin/env bash
set -euo pipefail

gateway_url="${GATEWAY_URL:-http://localhost:8080}"
query_url="${QUERY_URL:-http://localhost:8085}"
market_data_url="${MARKET_DATA_URL:-http://localhost:8086}"

client_order_id="demo-$(date +%s)"

echo "Placing order via ${gateway_url}/orders"
order_response="$(
  curl -sS -X POST "${gateway_url}/orders" \
    -H "Content-Type: application/json" \
    -d "{
      \"accountId\":\"ACC-001\",
      \"clientOrderId\":\"${client_order_id}\",
      \"symbol\":\"AAPL\",
      \"side\":\"BUY\",
      \"price\":100,
      \"quantity\":1
    }"
)"
echo "${order_response}"

order_id="$(printf '%s' "${order_response}" | sed -n 's/.*"orderId":"\([^"]*\)".*/\1/p')"
if [[ -z "${order_id}" ]]; then
  echo "Could not parse orderId from response" >&2
  exit 1
fi

echo
echo "Query projection"
curl -sS "${query_url}/orders/${order_id}"
echo
echo
echo "Balance projection"
curl -sS "${query_url}/accounts/ACC-001/balances"
echo
echo
echo "Market data snapshot"
curl -sS "${market_data_url}/market-data/AAPL/orderbook"
echo
