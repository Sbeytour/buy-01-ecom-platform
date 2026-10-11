import { computed, inject, Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { AuthResponse, LoginRequest, RegisterRequest, RegisterResponse } from '../models/auth';
import { User } from '../models/user';
import { catchError, map, Observable, of, switchMap, tap } from 'rxjs';
import { MediaService } from './media.service';
import { UserService } from './user.service';
import { environment } from '../../../environments/environment';

@Injectable({
  providedIn: 'root',
})
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly mediaService = inject(MediaService);
  private readonly userService = inject(UserService);
  private pendingAvatar: { userId: string; file: File } | null = null;
  readonly avatarUploadWarning = signal('');

  private readonly TOKEN_KEY = environment.auth.tokenStorageKey;

  public currentUser = signal<User | null>(null);
  public readonly isAuthenticated = computed(() => !!this.currentUser());

  register(payload: RegisterRequest, avatar?: File | null): Observable<User> {
    return this.http.post<RegisterResponse>(`/auth/register`, payload).pipe(
      tap((response) => {
        this.pendingAvatar = avatar ? { userId: response.user.userId, file: avatar } : null;
      }),
      map((response) => response.user),
    );
  }

  login(payload: LoginRequest): Observable<User> {
    this.avatarUploadWarning.set('');
    return this.http.post<AuthResponse>(`/auth/login`, payload).pipe(
      tap((response) => this.setSession(response)),
      switchMap((response) => {
        const pending = this.pendingAvatar;
        if (pending?.userId !== response.user.userId) {
          return of(response.user);
        }
        return this.mediaService.uploadProfile(pending.file).pipe(
          switchMap((image) => this.userService.updateProfile({ avatarMediaId: image.avatar.id })),
          tap((user) => {
            this.currentUser.set(user);
            this.pendingAvatar = null;
          }),
          catchError(() => {
            this.avatarUploadWarning.set('Account logged in, but avatar upload failed. You can retry from profile settings.');
            return of(response.user);
          }),
        );
      }),
    );
  }

  logout(redirectTo = '/login'): void {
    this.currentUser.set(null);
    localStorage.removeItem(this.TOKEN_KEY);

    this.router.navigateByUrl(redirectTo);
  }

  getToken(): string | null {
    return localStorage.getItem(this.TOKEN_KEY);
  }

  private setSession(response: AuthResponse): void {
    localStorage.setItem(this.TOKEN_KEY, response.token);
    this.currentUser.set(response.user);
  }

  loadCurrentUser(): Observable<User> {
    return this.http.get<User>(`/users/me`).pipe(
      tap((user) => {
        this.currentUser.set(user);
      }),
    );
  }
}
