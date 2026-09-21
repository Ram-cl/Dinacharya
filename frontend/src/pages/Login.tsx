import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Link, useNavigate } from 'react-router-dom';
import { useLogin } from '@/hooks/useAuth';
import { useEffect } from 'react';
import { useAuthStore } from '@/store/authStore';
import { homePath } from '@/auth/roles';
import Logo from '@/components/Logo';

const loginSchema = z.object({
  email: z.string().email('Invalid email address'),
  password: z.string().min(1, 'Password is required'),
});

type LoginForm = z.infer<typeof loginSchema>;

export default function Login() {
  const navigate = useNavigate();
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated());
  const loginMutation = useLogin();

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<LoginForm>({
    resolver: zodResolver(loginSchema),
  });

  const user = useAuthStore((state) => state.user);

  useEffect(() => {
    if (isAuthenticated) {
      navigate(homePath(user));
    }
  }, [isAuthenticated, navigate, user]);

  const onSubmit = async (data: LoginForm) => {
    try {
      const result = await loginMutation.mutateAsync({
        ...data,
        email: data.email.trim().toLowerCase(),
      });
      navigate(homePath(result.user));
    } catch {
      // toast is shown by useLogin
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center py-12 px-4 sm:px-6 lg:px-8 bg-linen">
      <div className="max-w-md w-full space-y-8">
        {/* Logo and Header */}
        <div className="text-center">
          <div className="flex justify-center mb-6">
            <Logo size="lg" />
          </div>
          <h2 className="text-display-lg text-charcoal text-center">
            Welcome to Dinacharya
          </h2>
          <p className="mt-2 text-body-lg text-charcoal-muted text-center">
            Sign in to your workspace
          </p>
        </div>
        
        {/* Login Form */}
        <div className="bg-ivory rounded-2xl p-8 shadow-elevated border border-warm-border">
          <form className="space-y-6" onSubmit={handleSubmit(onSubmit)}>
            <div>
              <label htmlFor="email" className="block text-label-md text-charcoal-muted mb-2">
                Email Address
              </label>
              <input
                {...register('email')}
                type="email"
                autoComplete="username"
                className="input"
                placeholder="you@company.com"
              />
              {errors.email && (
                <p className="mt-2 text-label-sm text-error">{errors.email.message}</p>
              )}
            </div>
            
            <div>
              <div className="flex items-center justify-between mb-2">
                <label htmlFor="password" className="block text-label-md text-charcoal-muted">
                  Password
                </label>
                <Link to="/forgot-password" className="text-label-sm text-terracotta hover:text-terracotta-dark">
                  Forgot password?
                </Link>
              </div>
              <input
                {...register('password')}
                type="password"
                autoComplete="current-password"
                className="input"
                placeholder="Your password"
              />
              {errors.password && (
                <p className="mt-2 text-label-sm text-error">{errors.password.message}</p>
              )}
            </div>

            <button
              type="submit"
              disabled={loginMutation.isPending}
              className="w-full btn btn-primary py-3 flex items-center justify-center gap-2"
            >
              {loginMutation.isPending ? (
                <>
                  <span className="material-symbols-outlined animate-spin text-[18px]">refresh</span>
                  Signing in...
                </>
              ) : (
                <>
                  <span className="material-symbols-outlined text-[18px]">login</span>
                  Sign in
                </>
              )}
            </button>
          </form>

          <div className="mt-6 text-center">
            <p className="text-body-md text-charcoal-muted">
              Don't have an account?{' '}
              <Link to="/register" className="text-terracotta hover:text-terracotta-dark font-medium transition-colors">
                Create one
              </Link>
            </p>
          </div>
        </div>
      </div>
    </div>
  );
}
