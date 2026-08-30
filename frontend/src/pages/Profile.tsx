import { useState } from 'react';
import { useAuthStore } from '@/store/authStore';
import { useUpdateProfile } from '@/hooks/useUsers';

export default function Profile() {
  const user = useAuthStore((state) => state.user);
  const updateProfile = useUpdateProfile();

  const [editingName, setEditingName] = useState(false);
  const [nameValue, setNameValue] = useState('');

  if (!user) return null;

  const handleEditStart = () => {
    setNameValue(user.name);
    setEditingName(true);
  };

  const handleCancel = () => {
    setEditingName(false);
    setNameValue('');
  };

  const handleSave = async () => {
    const trimmed = nameValue.trim();
    if (!trimmed || trimmed === user.name) {
      setEditingName(false);
      return;
    }
    await updateProfile.mutateAsync({ name: trimmed });
    setEditingName(false);
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter') handleSave();
    if (e.key === 'Escape') handleCancel();
  };

  return (
    <div className="max-w-2xl space-y-6">
      <h1 className="text-display-lg text-charcoal">Profile</h1>
      <div className="card space-y-6">
        {/* Header */}
        <div className="flex items-center gap-4 pb-6 border-b border-warm-border">
          <div className="avatar-lg bg-terracotta border-terracotta-dark">
            {user.name.charAt(0).toUpperCase()}
          </div>
          <div className="min-w-0">
            <p className="font-display text-2xl text-charcoal">{user.name}</p>
            <p className="text-body-md text-charcoal-muted">{user.email}</p>
          </div>
        </div>

        {/* Name field — inline editable */}
        <div>
          <label className="block text-label-md text-charcoal-muted mb-1">Name</label>
          {editingName ? (
            <div className="flex items-center gap-2">
              <input
                type="text"
                value={nameValue}
                onChange={(e) => setNameValue(e.target.value)}
                onKeyDown={handleKeyDown}
                autoFocus
                className="flex-1 px-3 py-1.5 rounded-lg border border-warm-border bg-white text-body-lg text-charcoal focus:outline-none focus:ring-2 focus:ring-terracotta/40"
                maxLength={255}
                minLength={2}
              />
              <button
                type="button"
                onClick={handleSave}
                disabled={updateProfile.isPending}
                className="btn btn-primary text-sm px-3 py-1.5"
              >
                {updateProfile.isPending ? 'Saving…' : 'Save'}
              </button>
              <button
                type="button"
                onClick={handleCancel}
                className="btn btn-secondary text-sm px-3 py-1.5"
              >
                Cancel
              </button>
            </div>
          ) : (
            <div className="flex items-center gap-2 group">
              <p className="text-body-lg text-charcoal">{user.name}</p>
              <button
                type="button"
                onClick={handleEditStart}
                title="Edit name"
                className="opacity-0 group-hover:opacity-100 transition-opacity p-1 rounded text-charcoal-muted hover:text-terracotta hover:bg-sand"
              >
                <span className="material-symbols-outlined text-[18px]">edit</span>
              </button>
            </div>
          )}
        </div>

        {/* Email */}
        <div>
          <label className="block text-label-md text-charcoal-muted mb-1">Email</label>
          <p className="text-body-lg text-charcoal">{user.email}</p>
        </div>

        {/* Role */}
        <div>
          <label className="block text-label-md text-charcoal-muted mb-1">Role</label>
          <span className="badge badge-secondary">{user.role}</span>
        </div>

        {user.department && (
          <div>
            <label className="block text-label-md text-charcoal-muted mb-1">Department</label>
            <p className="text-body-lg text-charcoal">{user.department}</p>
          </div>
        )}

        {user.joiningDate && (
          <div>
            <label className="block text-label-md text-charcoal-muted mb-1">Joining Date</label>
            <p className="text-body-lg text-charcoal">
              {new Date(user.joiningDate).toLocaleDateString('en-GB', {
                day: '2-digit',
                month: 'long',
                year: 'numeric',
              })}
            </p>
          </div>
        )}

        {user.bio && (
          <div>
            <label className="block text-label-md text-charcoal-muted mb-1">Bio</label>
            <p className="text-body-lg text-charcoal">{user.bio}</p>
          </div>
        )}
      </div>
    </div>
  );
}
