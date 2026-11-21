const PIT_COUNT = 12;
const INITIAL_DAN = 5;
const QUAN_VALUE = 10;
const REFILL_COST = 5;

const nextPit = (pit, direction) => (pit + direction + PIT_COUNT) % PIT_COUNT;
const isQuanPit = (pit) => pit === 0 || pit === 6;
const isEmpty = (state, pit) => state.dan[pit] === 0 && !state.quan[pit];
const firstPit = (player) => (player === 0 ? 1 : 7);

function finish(state, loser = null) {
  const next = { ...state, dan: [...state.dan], quan: [...state.quan], scores: [...state.scores], gameOver: true };
  for (let player = 0; player < 2; player += 1) {
    for (let pit = firstPit(player); pit < firstPit(player) + 5; pit += 1) {
      next.scores[player] += next.dan[pit];
      next.dan[pit] = 0;
    }
  }
  next.winner = loser === null
    ? (next.scores[0] === next.scores[1] ? -1 : next.scores[0] > next.scores[1] ? 0 : 1)
    : 1 - loser;
  return next;
}

function prepareTurn(state) {
  const start = firstPit(state.turn);
  if (Array.from({ length: 5 }, (_, i) => state.dan[start + i]).some((count) => count > 0)) return state;
  if (state.scores[state.turn] < REFILL_COST) return finish(state, state.turn);
  const next = { ...state, dan: [...state.dan], scores: [...state.scores] };
  next.scores[next.turn] -= REFILL_COST;
  for (let pit = start; pit < start + 5; pit += 1) next.dan[pit] = 1;
  next.moveEvents = [...(next.moveEvents ?? []), { type: "REFILL", pit: -1, value: next.turn }];
  return next;
}

export function createLocalGame(playerNames, startingPlayer = 0, series = [0, 0, 0]) {
  const dan = Array(PIT_COUNT).fill(INITIAL_DAN);
  dan[0] = 0;
  dan[6] = 0;
  return {
    mode: "LOCAL", local: true, playerNames, startingPlayer, dan,
    quan: Array.from({ length: PIT_COUNT }, (_, pit) => isQuanPit(pit)),
    scores: [0, 0], turn: startingPlayer, gameOver: false, passCounts: [0, 0], moveEvents: [],
    series: [...series],
  };
}

export function playLocalMove(state, pit, direction) {
  if (state.gameOver || ![1, -1].includes(direction)
    || pit < firstPit(state.turn) || pit >= firstPit(state.turn) + 5 || state.dan[pit] < 1) return state;

  const next = { ...state, dan: [...state.dan], quan: [...state.quan], scores: [...state.scores], passCounts: [...state.passCounts], moveEvents: [] };
  let position = pit;
  let hand = next.dan[position];
  next.dan[position] = 0;
  const moveEvents = [{ type: "PICKUP", pit: position, value: hand }];
  let gained = 0;
  let loops = 0;
  while (hand > 0 && loops++ < 1000) {
    while (hand > 0) {
      position = nextPit(position, direction);
      next.dan[position] += 1;
      hand -= 1;
      moveEvents.push({ type: "SOW", pit: position, value: 1 });
    }
    const following = nextPit(position, direction);
    if (!isQuanPit(following) && next.dan[following] > 0) {
      position = following;
      hand = next.dan[position];
      next.dan[position] = 0;
      moveEvents.push({ type: "PICKUP", pit: position, value: hand });
      continue;
    }
    if (isQuanPit(following) && !isEmpty(next, following)) break;

    let cursor = following;
    while (isEmpty(next, cursor)) {
      const target = nextPit(cursor, direction);
      if (isEmpty(next, target)) break;
      const captured = next.dan[target] + (next.quan[target] ? QUAN_VALUE : 0);
      gained += captured;
      next.dan[target] = 0;
      next.quan[target] = false;
      moveEvents.push({ type: "CAPTURE", pit: target, value: captured });
      if (isEmpty(next, 0) && isEmpty(next, 6)) break;
      cursor = nextPit(target, direction);
    }
    break;
  }
  if (hand > 0) next.dan[position] += hand;
  next.scores[next.turn] += gained;
  next.moveEvents = moveEvents;
  if (isEmpty(next, 0) && isEmpty(next, 6)) return finish(next);
  next.turn = 1 - next.turn;
  return prepareTurn(next);
}

export function passLocalTurn(state) {
  if (state.gameOver) return state;
  const next = { ...state, passCounts: [...state.passCounts], moveEvents: [] };
  next.passCounts[state.turn] += 1;
  if (next.passCounts[state.turn] >= 2) return finish(next, state.turn);
  const start = firstPit(state.turn);
  const legalPits = Array.from({ length: 5 }, (_, offset) => start + offset)
    .filter((pit) => next.dan[pit] > 0);
  if (!legalPits.length) return finish(next, state.turn);
  const pit = legalPits[Math.floor(Math.random() * legalPits.length)];
  return playLocalMove(next, pit, Math.random() < 0.5 ? -1 : 1);
}

export function timeoutLocalTurn(state) {
  if (state.gameOver) return state;
  const start = firstPit(state.turn);
  const legalPits = Array.from({ length: 5 }, (_, offset) => start + offset)
    .filter((pit) => state.dan[pit] > 0);
  if (!legalPits.length) return finish(state, state.turn);
  const pit = legalPits[Math.floor(Math.random() * legalPits.length)];
  const direction = Math.random() < 0.5 ? -1 : 1;
  const next = playLocalMove(state, pit, direction);
  if (next.passCounts) next.passCounts = [0, 0];
  return next;
}
