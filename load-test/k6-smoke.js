import http from "k6/http";
import { check, sleep } from "k6";

export const options = {
  vus: 5,
  iterations: 25,
};

export default function () {
  const payload = JSON.stringify({
    accountId: "ACC-001",
    clientOrderId: `cli-${__VU}-${__ITER}`,
    symbol: "AAPL",
    side: "BUY",
    price: "100",
    quantity: 1,
  });

  const response = http.post("http://localhost:8080/orders", payload, {
    headers: { "Content-Type": "application/json" },
  });

  check(response, {
    "status is 201": (r) => r.status === 201,
  });

  sleep(1);
}
