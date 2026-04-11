import { ComposerSection } from "@/features/post/components/ComposerSection";
import { FeedList } from "@/features/post/components/FeedList";

/**
 * 홈 피드.
 * Server Component — 정적 셸 + Client Component(피드/작성기) 를 조합한다.
 */
export default function Home() {
  return (
    <main className="mx-auto w-full max-w-2xl flex-1">
      <ComposerSection />
      <FeedList />
    </main>
  );
}
