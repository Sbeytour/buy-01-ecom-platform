import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ANIMATION_MODULE_TYPE } from '@angular/platform-browser/animations';
import { HttpErrorResponse } from '@angular/common/http';
import { Router, provideRouter } from '@angular/router';
import { signal } from '@angular/core';
import { of, throwError } from 'rxjs';

import { Register } from './register';
import { AuthService } from '../../../core/services/auth.service';
import { MediaService } from '../../../core/services/media.service';
import { UserService } from '../../../core/services/user.service';
import { User } from '../../../core/models/user';

describe('Register Component', () => {
  let component: Register;
  let fixture: ComponentFixture<Register>;
  let authServiceSpy: jasmine.SpyObj<AuthService>;
  let mediaServiceSpy: jasmine.SpyObj<MediaService>;
  let userServiceSpy: jasmine.SpyObj<UserService>;
  let router: Router;

  const mockUser: User = {
    userId: '1',
    email: 'test@test.com',
    username: 'TestUser',
    role: 'CLIENT',
    avatarMediaId: null,
  };

  beforeEach(async () => {
    // Mock dependencies
    authServiceSpy = jasmine.createSpyObj('AuthService', ['register'], {
      currentUser: signal<User | null>(null),
    });
    mediaServiceSpy = jasmine.createSpyObj('MediaService', ['uploadProfile']);
    userServiceSpy = jasmine.createSpyObj('UserService', ['updateProfile']);

    await TestBed.configureTestingModule({
      imports: [Register],
      providers: [
        { provide: AuthService, useValue: authServiceSpy },
        { provide: MediaService, useValue: mediaServiceSpy },
        { provide: UserService, useValue: userServiceSpy },
        provideRouter([]),
        { provide: ANIMATION_MODULE_TYPE, useValue: 'NoopAnimations' },
      ],
    }).compileComponents();

    router = TestBed.inject(Router);
    spyOn(router, 'navigateByUrl').and.returnValue(Promise.resolve(true));

    fixture = TestBed.createComponent(Register);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create the component', () => {
    expect(component).toBeTruthy();
  });

  describe('Form Initialization and Validation', () => {
    it('should initialize with an empty, invalid form and default CLIENT role', () => {
      expect(component.registerForm.valid).toBeFalse();
      expect(component.registerForm.controls.role.value).toBe('CLIENT');
    });

    it('should validate username constraints', () => {
      const usernameControl = component.registerForm.controls.username;

      usernameControl.setValue('a'); // too short
      expect(usernameControl.hasError('minlength')).toBeTrue();

      usernameControl.setValue('aVeryLongUsernameThatExceeds15'); // too long
      expect(usernameControl.hasError('maxlength')).toBeTrue();

      usernameControl.setValue('invalid@name!'); // bad pattern
      expect(usernameControl.hasError('pattern')).toBeTrue();

      usernameControl.setValue('ValidUser123'); // valid
      expect(usernameControl.errors).toBeNull();
    });

    it('should validate email and password as required', () => {
      const { email, password } = component.registerForm.controls;

      email.setValue('not-an-email');
      expect(email.hasError('email')).toBeTrue();

      password.setValue('short');
      expect(password.hasError('minlength')).toBeTrue();
    });
  });

  describe('UI Interactions', () => {
    it('should update role when selectRole is called', () => {
      component.selectRole('SELLER');
      expect(component.registerForm.controls.role.value).toBe('SELLER');
    });

    it('should toggle password visibility signal', () => {
      expect(component.hidePassword()).toBeTrue();
      component.togglePasswordVisibility();
      expect(component.hidePassword()).toBeFalse();
    });

    it('should correctly format frontend error messages', () => {
      const usernameControl = component.registerForm.controls.username;
      usernameControl.markAsTouched();

      usernameControl.setValue('');
      expect(component.getErrorMessage('username')).toBe('username is required');

      usernameControl.setValue('ab');
      expect(component.getErrorMessage('username')).toBe('must be at least 4 characters');
    });
  });

  describe('Avatar Selection', () => {
    beforeEach(() => {
      spyOn(URL, 'createObjectURL').and.returnValue('blob:mock-url');
    });

    it('should reject non-image files', () => {
      const mockEvent = { target: { files: [new File([''], 'test.txt', { type: 'text/plain' })] } } as unknown as Event;
      component.onAvatarSelected(mockEvent);

      expect(component.submitError()).toBe('Avatar must be an image file.');
      expect(component.avatarPreview()).toBeNull();
    });

    it('should reject files larger than 2MB', () => {
      // Create a dummy file artificially reporting > 2MB size
      const mockFile = new File([''], 'large.jpg', { type: 'image/jpeg' });
      Object.defineProperty(mockFile, 'size', { value: 3 * 1024 * 1024 });
      const mockEvent = { target: { files: [mockFile] } } as unknown as Event;

      component.onAvatarSelected(mockEvent);
      expect(component.submitError()).toBe('Avatar must be 2 MB or less.');
    });

    it('should accept valid images and create a preview', () => {
      const mockFile = new File([''], 'avatar.png', { type: 'image/png' });
      const mockEvent = { target: { files: [mockFile] } } as unknown as Event;

      component.onAvatarSelected(mockEvent);
      expect(component.submitError()).toBe('');
      expect(component.avatarPreview()).toBe('blob:mock-url');
    });

    it('should clear avatar state', () => {
      component.avatarPreview.set('blob:mock-url');
      component.clearAvatar();
      expect(component.avatarPreview()).toBeNull();
    });
  });

  describe('Submission Logic', () => {
    const validFormData = {
      role: 'CLIENT' as const,
      username: 'TestUser',
      email: 'test@example.com',
      password: 'password123',
    };

    it('should not submit if the form is invalid', () => {
      component.onSubmit();
      expect(authServiceSpy.register).not.toHaveBeenCalled();
    });

    it('should trim string values on submit', () => {
      authServiceSpy.register.and.returnValue(of(mockUser));
      component.registerForm.setValue({ ...validFormData, username: '  TestUser  ' });
      component.onSubmit();

      // Check if it trimmed the value successfully
      expect(component.registerForm.value.username).toBe('TestUser');
    });

    it('should register a client and navigate to login', () => {
      authServiceSpy.register.and.returnValue(of(mockUser));
      component.registerForm.setValue(validFormData);

      component.onSubmit();

      expect(component.isSubmitting()).toBeFalse();
      expect(authServiceSpy.register).toHaveBeenCalledWith(validFormData, null);
      expect(mediaServiceSpy.uploadProfile).not.toHaveBeenCalled();
      expect(router.navigateByUrl).toHaveBeenCalledWith('/login');
    });

    it('should redirect sellers to login without authenticating them', () => {
      authServiceSpy.register.and.returnValue(of({ ...mockUser, role: 'SELLER' }));
      component.registerForm.setValue({ ...validFormData, role: 'SELLER' });
      component.onSubmit();
      expect(router.navigateByUrl).toHaveBeenCalledWith('/login');
      expect(authServiceSpy.currentUser()).toBeNull();
      expect(mediaServiceSpy.uploadProfile).not.toHaveBeenCalled();
    });

    it('should retain the selected photo for login without uploading at registration', () => {
      spyOn(URL, 'createObjectURL').and.returnValue('blob:avatar');
      spyOn(URL, 'revokeObjectURL');
      const file = new File(['image'], 'avatar.png', { type: 'image/png' });
      component.onAvatarSelected({ target: { files: [file], value: '' } } as unknown as Event);
      authServiceSpy.register.and.returnValue(of(mockUser));
      component.registerForm.setValue(validFormData);
      component.onSubmit();
      expect(authServiceSpy.register).toHaveBeenCalledWith(validFormData, file);
      expect(mediaServiceSpy.uploadProfile).not.toHaveBeenCalled();
      expect(router.navigateByUrl).toHaveBeenCalledWith('/login');
    });

    it('should handle backend generic errors correctly', () => {
      const errorResponse = new HttpErrorResponse({ error: { message: 'Email already exists' }, status: 400 });
      authServiceSpy.register.and.returnValue(throwError(() => errorResponse));

      component.registerForm.setValue(validFormData);
      component.onSubmit();

      expect(component.submitError()).toBe('Email already exists');
    });
  });
});