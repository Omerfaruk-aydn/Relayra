export interface MockUser {
  id: string;
  name: string;
  handle: string;
  role: string;
  statusText: string;
  presence: "online" | "idle" | "offline";
  activeAgo: string;
  mutualFriends: number;
  location?: string;
  bio?: string;
}

export const MOCK_USERS: MockUser[] = [
  { id: "u-sarah", name: "Sarah Kim", handle: "@sarahkim", role: "Product Designer", statusText: "Online", presence: "online", activeAgo: "Active now", mutualFriends: 12, location: "San Francisco, CA", bio: "Designing intuitive and accessible experiences." },
  { id: "u-daniel", name: "Daniel Park", handle: "@dpark", role: "UX Researcher", statusText: "Online", presence: "online", activeAgo: "Active 12m ago", mutualFriends: 8 },
  { id: "u-priya", name: "Priya Shah", handle: "@priyashah", role: "Design Engineer", statusText: "Online", presence: "online", activeAgo: "Active 28m ago", mutualFriends: 15 },
  { id: "u-marcus", name: "Marcus Lee", handle: "@marcuslee", role: "Frontend Engineer", statusText: "Online", presence: "online", activeAgo: "Active 1h ago", mutualFriends: 9 },
  { id: "u-emma", name: "Emma Wilson", handle: "@emmawilson", role: "Product Manager", statusText: "In a meeting", presence: "idle", activeAgo: "Active 3h ago", mutualFriends: 6 },
  { id: "u-ryan", name: "Ryan Tan", handle: "@ryantan", role: "UI Engineer", statusText: "Do not disturb", presence: "idle", activeAgo: "Active 5h ago", mutualFriends: 14 },
  { id: "u-olivia", name: "Olivia Carter", handle: "@oliviacarter", role: "Content Designer", statusText: "Online", presence: "online", activeAgo: "Active 6h ago", mutualFriends: 11 },
  { id: "u-noah", name: "Noah Kim", handle: "@noahkim", role: "Design Engineer", statusText: "Away", presence: "offline", activeAgo: "Active 4h ago", mutualFriends: 7 },
  { id: "u-ava", name: "Ava Patel", handle: "@avapatel", role: "Accessibility Specialist", statusText: "Online", presence: "online", activeAgo: "Active now", mutualFriends: 5 },
  { id: "u-liam", name: "Liam Brooks", handle: "@liambrooks", role: "Product Designer", statusText: "Offline", presence: "offline", activeAgo: "Active 1d ago", mutualFriends: 4 },
];

export interface MockChannel {
  id: string;
  name: string;
  topic: string;
  members: number;
}

export const MOCK_CHANNELS: MockChannel[] = [
  { id: "c-announcements", name: "announcements", topic: "Important updates from the team", members: 12842 },
  { id: "c-product-design", name: "product-design", topic: "Design systems, product experience, and the craft of great software", members: 1284 },
  { id: "c-design-systems", name: "design-systems", topic: "Tokens, components, and patterns", members: 4218 },
  { id: "c-prototyping", name: "prototyping", topic: "Prototypes and work in progress", members: 3675 },
  { id: "c-accessibility", name: "accessibility", topic: "Inclusive design practices", members: 2931 },
  { id: "c-jobs", name: "jobs", topic: "Design and product roles", members: 1842 },
  { id: "c-show-and-tell", name: "show-and-tell", topic: "Share your work and get feedback", members: 5100 },
  { id: "c-feedback", name: "feedback", topic: "Get and give feedback", members: 1834 },
];

export interface MockMessage {
  id: string;
  authorId: string;
  time: string;
  content: string;
  reactions: { emoji: string; count: number; active?: boolean }[];
}

export const MOCK_MESSAGES: MockMessage[] = [
  {
    id: "m-1",
    authorId: "u-sarah",
    time: "Today at 10:14 AM",
    content: "Just shipped a small but meaningful update to our design system — new semantic color tokens and elevated surface styles.",
    reactions: [{ emoji: "❤", count: 24 }, { emoji: "🎉", count: 18 }],
  },
  {
    id: "m-2",
    authorId: "u-daniel",
    time: "Today at 9:47 AM",
    content: "This looks fantastic! The new surface styles really add depth without feeling heavy. Are there any migration notes for existing components?",
    reactions: [{ emoji: "👍", count: 12 }],
  },
  {
    id: "m-3",
    authorId: "u-priya",
    time: "Today at 10:05 AM",
    content: "I put together a few usage examples in Figma, including light and dark mode, and some edge cases for data-heavy views.",
    reactions: [{ emoji: "❤", count: 9 }, { emoji: "🔥", count: 6 }],
  },
];

export interface MockCommunity {
  id: string;
  name: string;
  handle: string;
  description: string;
  members: number;
  tags: string[];
  joined?: boolean;
}

export const MOCK_COMMUNITIES: MockCommunity[] = [
  { id: "com-relayra", name: "Relayra Design", handle: "@product-design", description: "A community for designers, builders, and product people creating the future of software with Relayra.", members: 12842, tags: ["Design", "Product", "UI/UX"], joined: true },
  { id: "com-product", name: "Product Design", handle: "@product", description: "Discussions, resources, and feedback for product designers at every stage.", members: 8421, tags: ["Design", "Research", "Prototyping"] },
  { id: "com-systems", name: "Design Systems", handle: "@systems", description: "Build, scale, and ship better design systems, tools, and real-world experiences.", members: 6328, tags: ["Design", "Systems", "Engineering"] },
];

export interface MockNotification {
  id: string;
  authorId: string;
  text: string;
  time: string;
  kind: string;
}

export const MOCK_NOTIFICATIONS: MockNotification[] = [
  { id: "n-1", authorId: "u-sarah", text: "sent you a friend request", time: "2m ago", kind: "Friend request" },
  { id: "n-2", authorId: "u-daniel", text: "mentioned you in #product-design", time: "18m ago", kind: "Mention" },
  { id: "n-3", authorId: "u-priya", text: "sent you a direct message", time: "1h ago", kind: "Direct message" },
  { id: "n-4", authorId: "u-marcus", text: "accepted your invite", time: "5h ago", kind: "Invite" },
  { id: "n-5", authorId: "u-emma", text: "was made a Manager by Alex Chen", time: "Yesterday", kind: "Role change" },
];
