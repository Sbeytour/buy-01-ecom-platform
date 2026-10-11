import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { ReactiveFormsModule, FormGroup, FormControl, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { finalize } from 'rxjs';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatFormFieldModule,
    MatInputModule,
    MatIconModule,
    MatButtonModule,
  ],
  templateUrl: './login.html',
  styleUrl: './login.scss',
})
export class Login {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  readonly hidePassword = signal(true);
  readonly isSubmitting = signal(false);
  readonly submitError = signal('');
  readonly avatarUploadWarning = signal('');
  readonly continuePath = signal('/shop');

  readonly loginForm = new FormGroup({
    email: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.email],
    }),
    password: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required],
    }),
  });

  fieldErrors = signal<{ [key: string]: string }>({});

  togglePasswordVisibility(): void {
    this.hidePassword.update((hide) => !hide);
  }

  onSubmit(): void {
    this.fieldErrors.set({});
    this.submitError.set('');

    if (this.loginForm.invalid) {
      return;
    }

    const payload = this.loginForm.getRawValue();
    this.isSubmitting.set(true);

    this.authService
      .login(payload)
      .pipe(finalize(() => this.isSubmitting.set(false)))
      .subscribe({
        next: (user) => {
          const redirectPath = user.role === 'SELLER' ? '/seller' : '/shop';
          const warning = this.authService.avatarUploadWarning();
          if (warning) {
            this.avatarUploadWarning.set(warning);
            this.continuePath.set(redirectPath);
          } else {
            void this.router.navigateByUrl(redirectPath);
          }
        },
        error: (error: HttpErrorResponse) => this.handleSubmitError(error),
      });
  }

  getErrorMessage(fieldName: string): string {
    const backendError = this.fieldErrors()[fieldName];
    if (backendError) {
      return backendError;
    }

    const field = this.loginForm.get(fieldName);

    if (!field || field.valid || field.untouched) {
      return '';
    }

    const errors = field.errors;
    if (!errors) {
      return '';
    }

    if (errors['required']) {
      return `${fieldName} is required`;
    }

    if (errors['email']) {
      return 'enter a valid email';
    }

    return '';
  }

  private handleSubmitError(error: HttpErrorResponse): void {
    if (error.error?.details) {
      const details = error.error.details;
      this.fieldErrors.set(details);

      Object.keys(details).forEach((fieldName) => {
        const control = this.loginForm.get(fieldName);
        if (control) {
          control.setErrors({ backend: details[fieldName] });
          control.markAsTouched();
        }
      });
      return;
    }

    if (error.error?.message) {
      const message = error.error.message;

      this.submitError.set(message);

      return;
    }

    if (error.error?.error) {
      this.submitError.set(error.error.error);
    } else {
      this.submitError.set('Login failed');
    }
  }
}
