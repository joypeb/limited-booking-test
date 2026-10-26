import { ConsoleProvider } from "@/components/ConsoleProvider";
import { CredentialsPanel } from "@/components/CredentialsPanel";
import { ResourcePanel } from "@/components/ResourcePanel";
import { ReservationCreatePanel } from "@/components/ReservationCreatePanel";
import { ReservationPanel } from "@/components/ReservationPanel";
import { RecentHistory } from "@/components/RecentHistory";

export default function Home() {
  return (
    <main className="min-h-screen bg-slate-50 px-4 py-8 text-slate-900">
      <div className="mx-auto max-w-4xl space-y-6">
        <h1 className="text-3xl font-bold">한정 수량 예약 콘솔</h1>
        <ConsoleProvider>
          <CredentialsPanel />
          <ResourcePanel />
          <ReservationCreatePanel />
          <ReservationPanel />
          <RecentHistory />
        </ConsoleProvider>
      </div>
    </main>
  );
}
