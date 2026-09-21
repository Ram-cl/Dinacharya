import TeamAttendanceSection from '@/components/TeamAttendanceSection';

export default function ModeratorPanel() {
  return (
    <div className="space-y-8">
      {/* Header */}
      <div>
        <h1 className="text-display-lg text-charcoal">Moderator Panel</h1>
        <p className="text-body-lg text-charcoal-muted mt-1">
          Manage team attendance
        </p>
      </div>

      {/* Team Attendance Section */}
      <div className="card border-0 shadow-sm">
        <TeamAttendanceSection />
      </div>
    </div>
  );
}
