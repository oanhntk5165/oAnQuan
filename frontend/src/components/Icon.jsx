export default function Icon({ name, size = 20 }) {
  const paths = {
    "people-solid": <><circle cx="12" cy="7" r="4" /><circle cx="4" cy="9" r="3" /><circle cx="20" cy="9" r="3" /><path d="M0 21v-3a5 5 0 0 1 7-4.6V21ZM24 21v-3a5 5 0 0 0-7-4.6V21Z" /><path d="M5 22v-3a7 7 0 0 1 14 0v3Z" /></>,
    "plus-circle": <><circle cx="12" cy="12" r="9" /><path d="M12 7v10M7 12h10" /></>,
    info: <><circle cx="12" cy="12" r="9" /><path d="M12 11v6M12 7h.01" /></>,
    clock: <><circle cx="12" cy="12" r="9" /><path d="M12 6v6l4 2" /></>,
    key: <><circle cx="16" cy="8" r="5" /><path d="m12.5 11.5-9 9L2 19l2-2-1-1 3-3 2 1 2-2" /></>,
    copy: <><rect x="7" y="3" width="14" height="16" rx="2" /><path d="M17 21H5a2 2 0 0 1-2-2V7" /></>,
    monitor: <><rect x="3" y="3" width="18" height="14" rx="2" /><path d="M12 17v4M8 21h8" /></>,
    lock: <><rect x="4" y="10" width="16" height="12" rx="2" /><path d="M8 10V6a4 4 0 0 1 8 0v4M12 15v3" /></>,
    eye: <><path d="M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12Z" /><circle cx="12" cy="12" r="3" /></>,
    "eye-off": <><path d="m3 3 18 18M10 5a12 12 0 0 1 12 7l-3 4M6 6a18 18 0 0 0-4 6s4 7 10 7c2 0 4-.7 5-2M10 10a3 3 0 0 0 4 4" /></>,
    "arrow-right": <><path d="M3 12h18m-9-9 9 9-9 9" /></>,
    chat: <><path d="M21 11a9 8 0 0 1-9 8H7l-5 3 2-6a8 8 0 1 1 17-5Z" /><path d="M8 11h.01M12 11h.01M16 11h.01" /></>,
    leaf: <><path d="M20 4c-8 0-14 4-14 11a5 5 0 0 0 5 5c7 0 11-6 9-16Z" /><path d="M4 21c3-5 7-8 12-11" /></>,
    home: <><path d="m3 10 9-7 9 7" /><path d="M5 9v11h14V9M9 20v-6h6v6" /></>,
    history: <><path d="M3 12a9 9 0 1 0 3-6.7L3 8" /><path d="M3 3v5h5m4-1v5l3 2" /></>,
    chart: <><path d="M4 19V5m0 14h17" /><path d="m7 15 4-4 3 2 6-7" /></>,
    book: <><path d="M4 5.5A2.5 2.5 0 0 1 6.5 3H20v17H6.5A2.5 2.5 0 0 1 4 17.5z" /><path d="M4 16h16M8 7h8m-8 4h6" /></>,
    user: <><circle cx="12" cy="8" r="4" /><path d="M5 21v-2a7 7 0 0 1 14 0v2z" /></>,
    search: <><circle cx="10.8" cy="10.8" r="6.8" /><path d="m16 16 4.5 4.5" /></>,
    play: <path d="m8 5 12 7-12 7V5Z" />,
    people: <><path d="M16 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" /><circle cx="10" cy="7" r="4" /><path d="M20 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75" /></>,
    trophy: <><path d="M8 21h8m-4-4v4M7 4h10v5a5 5 0 0 1-10 0V4ZM7 7H4v2a4 4 0 0 0 4 4m9-6h3v2a4 4 0 0 1-4 4" /></>,
    flag: <><path d="M5 21V5" /><path d="M5 5c5-4 9 4 14 0v10c-5 4-9-4-14 0" /></>,
    arrow: <><path d="M7 17 17 7M7 7h10v10" /></>,
    "arrow-left": <><path d="M19 12H5" /><path d="m12 19-7-7 7-7" /></>,
    pause: <><path d="M8 5v14M16 5v14" /></>,
    logout: <><path d="M10 17l5-5-5-5M15 12H3" /><path d="M12 3h6a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-6" /></>,
    send: <><path d="m22 2-7 20-4-9-9-4 20-7Z" /><path d="M22 2 11 13" /></>,
    close: <><path d="m18 6-12 12M6 6l12 12" /></>,
    refresh: <><path d="M20 7v5h-5M4 17v-5h5" /><path d="M5.6 9A7 7 0 0 1 18 6l2 2M4 16l2 2a7 7 0 0 0 12.4-3" /></>,
  };

  return (
    <svg aria-hidden="true" width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor"
      strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">{paths[name]}</svg>
  );
}
