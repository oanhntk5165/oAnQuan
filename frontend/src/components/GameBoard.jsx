const PEBBLE_IMAGES = [
  "/images/pebble.svg",
  "/images/pebble-green.svg",
  "/images/pebble-coral.svg",
  "/images/pebble-teal.svg",
];
const QUAN_VALUE = 10;

export function Pit({
  index,
  board,
  disabled,
  legal = false,
  selected = false,
  onClick,
  onChooseDirection,
}) {
  const quanPit = index === 0 || index === 6;
  const seedCount = board.dan?.[index] ?? 0;

  return (
    <div className={`pit-shell ${legal ? "pit-shell-legal" : ""} ${selected ? "pit-shell-selected" : ""}`}>
      <button
        className={`pit ${quanPit ? "pit-quan" : ""} ${disabled ? "pit-disabled" : "pit-playable"} ${legal ? "pit-legal" : ""} ${selected ? "pit-selected" : ""}`}
        disabled={disabled}
        aria-pressed={selected}
        onClick={onClick}
        aria-label={`${quanPit ? "Ô quan" : `Ô ${index}`}, ${seedCount} quân`}>
        <span className="seed-count">{seedCount}</span>
        <span className="seed-stack" aria-hidden="true">{Array.from({ length: Math.min(seedCount, 12) }, (_, seed) =>
          <img key={`${index}-${seed}`} src={PEBBLE_IMAGES[(seed + index) % PEBBLE_IMAGES.length]} alt="" draggable="false" />)}</span>
        {board.quan?.[index] && <img className="quan-stone" src="/images/quan.svg" alt="" aria-hidden="true" title="Quân quan" />}
      </button>
      {!disabled && selected && onChooseDirection && (
        <div className="pit-direction-tools" onClick={(event) => event.stopPropagation()}>
          <button type="button" className="pit-direction-button left" aria-label="Rải sang trái" onClick={(event) => {
            event.stopPropagation();
            onChooseDirection(-1);
          }}>←</button>
          <button type="button" className="pit-direction-button right" aria-label="Rải sang phải" onClick={(event) => {
            event.stopPropagation();
            onChooseDirection(1);
          }}>→</button>
        </div>
      )}
    </div>
  );
}

export function StorePit({ board, index }) {
  const seedCount = board.dan?.[index] ?? 0;
  const totalValue = seedCount + (board.quan?.[index] ? QUAN_VALUE : 0);

  return (
    <div className="store-pit">
      <span className="store-number">{totalValue}</span>
      <span className="seed-stack store-seed-stack" aria-hidden="true">{Array.from({ length: Math.min(seedCount, 12) }, (_, seed) =>
        <img key={`${index}-${seed}`} src={PEBBLE_IMAGES[(seed + index) % PEBBLE_IMAGES.length]} alt="" draggable="false" />)}</span>
      {board.quan?.[index] && <img className="store-stone" src="/images/quan.svg" alt="" aria-hidden="true" title="Quân quan" />}
    </div>
  );
}
