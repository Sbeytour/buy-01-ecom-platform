import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ANIMATION_MODULE_TYPE } from '@angular/platform-browser/animations';
import { HttpErrorResponse } from '@angular/common/http';
import { Router, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { signal } from '@angular/core';

import { Login } from './login';
import { AuthService } from '../../../core/services/auth.service';
import { User } from '../../../core/models/user';

describe('Login Component', () => {
  let component: Login;
  let fixture: ComponentFixture<Login>;
  let authServiceSpy: jasmine.SpyObj<AuthService>;
  let router: Router;

  beforeEach(async () => {
    // Create spies for our injected dependencies
    authServiceSpy = jasmine.createSpyObj('AuthService', ['login'], { avatarUploadWarning: signal('') });

    await TestBed.configureTestingModule({
      // Import the standalone component
      imports: [Login],
      providers: [
        { provide: AuthService, useValue: authServiceSpy },
        // Safely resolves RouterLink dependencies (like ActivatedRoute)
        provideRouter([]),
        // Required to test components using Angular Material
        { provide: ANIMATION_MODULE_TYPE, useValue: 'NoopAnimations' },
      ],
    }).compileComponents();

    // Inject the actual Router and spy on navigateByUrl
    router = TestBed.inject(Router);
    spyOn(router, 'navigateByUrl').and.returnValue(Promise.resolve(true));

    fixture = TestBed.createComponent(Login);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create the component', () => {
    expect(component).toBeTruthy();
  });

  describe('Form Initialization and Validation', () => {
    it('should initialize with an empty, invalid form', () => {
      expect(component.loginForm.valid).toBeFalse();
      expect(component.loginForm.value).toEqual({ email: '', password: '' });
    });

    it('should mark email as invalid if empty or incorrect format', () => {
      const emailControl = component.loginForm.controls.email;

      emailControl.setValue('');
      expect(emailControl.hasError('required')).toBeTrue();

      emailControl.setValue('not-an-email');
      expect(emailControl.hasError('email')).toBeTrue();

      emailControl.setValue('valid@example.com');
      expect(emailControl.errors).toBeNull();
    });

    it('should validate password as required', () => {
      const passwordControl = component.loginForm.controls.password;

      passwordControl.setValue('');
      expect(passwordControl.hasError('required')).toBeTrue();

      passwordControl.setValue('password123');
      expect(passwordControl.errors).toBeNull();
    });
  });

  describe('UI Interactions', () => {
    it('should toggle password visibility signal', () => {
      expect(component.hidePassword()).toBeTrue(); // Initial state

      component.togglePasswordVisibility();
      expect(component.hidePassword()).toBeFalse();

      component.togglePasswordVisibility();
      expect(component.hidePassword()).toBeTrue();
    });

    it('should return correct frontend error messages', () => {
      const emailControl = component.loginForm.controls.email;

      // Mark as touched to trigger the error message logic
      emailControl.markAsTouched();

      emailControl.setValue('');
      expect(component.getErrorMessage('email')).toBe('email is required');

      emailControl.setValue('invalid-email');
      expect(component.getErrorMessage('email')).toBe('enter a valid email');
    });
  });

  describe('Submission Logic', () => {
    it('should not submit if the form is invalid', () => {
      component.onSubmit();
      expect(authServiceSpy.login).not.toHaveBeenCalled();
    });

    it('should submit successfully and navigate to /shop for CLIENT', () => {
      const mockClientUser = { role: 'CLIENT' } as User;
      authServiceSpy.login.and.returnValue(of(mockClientUser));

      component.loginForm.setValue({ email: 'client@test.com', password: 'pw' });
      component.onSubmit();

      expect(component.isSubmitting()).toBeFalse();
      expect(authServiceSpy.login).toHaveBeenCalledWith({ email: 'client@test.com', password: 'pw' });
      expect(router.navigateByUrl).toHaveBeenCalledWith('/shop');
    });

    it('should submit successfully and navigate to /seller for SELLER', () => {
      const mockSellerUser = { role: 'SELLER' } as User;
      authServiceSpy.login.and.returnValue(of(mockSellerUser));

      component.loginForm.setValue({ email: 'seller@test.com', password: 'pw' });
      component.onSubmit();

      expect(authServiceSpy.login).toHaveBeenCalled();
      expect(router.navigateByUrl).toHaveBeenCalledWith('/seller');
    });

    it('should handle backend field-specific errors correctly', () => {
      const errorResponse = new HttpErrorResponse({
        error: { details: { email: 'Email not found' } },
        status: 400
      });
      authServiceSpy.login.and.returnValue(throwError(() => errorResponse));

      component.loginForm.setValue({ email: 'bad@test.com', password: 'pw' });
      component.onSubmit();

      expect(component.fieldErrors()['email']).toBe('Email not found');
      expect(component.getErrorMessage('email')).toBe('Email not found');
      expect(component.loginForm.controls.email.hasError('backend')).toBeTrue();
    });

    it('should handle backend generic message errors correctly', () => {
      const errorResponse = new HttpErrorResponse({
        error: { message: 'Invalid credentials' },
        status: 401
      });
      authServiceSpy.login.and.returnValue(throwError(() => errorResponse));

      component.loginForm.setValue({ email: 'test@test.com', password: 'wrong' });
      component.onSubmit();

      expect(component.submitError()).toBe('Invalid credentials');
    });

    it('should handle fallback generic errors correctly', () => {
      const errorResponse = new HttpErrorResponse({
        error: { error: 'Internal Server Error' },
        status: 500
      });
      authServiceSpy.login.and.returnValue(throwError(() => errorResponse));

      component.loginForm.setValue({ email: 'test@test.com', password: 'pw' });
      component.onSubmit();

      expect(component.submitError()).toBe('Internal Server Error');
    });
  });
});