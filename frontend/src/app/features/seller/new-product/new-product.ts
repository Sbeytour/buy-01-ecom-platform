import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, OnInit, signal } from '@angular/core';
import { ReactiveFormsModule, FormGroup, FormControl, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatIcon, MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { finalize, map, Observable, of, switchMap } from 'rxjs';
import { ProductService } from '../../../core/services/product.service';
import { Product } from '../../../core/models/product';
import { MediaService } from '../../../core/services/media.service';

interface ImagePreview {
  file: File | null;
  url: string;
  mediaId?: string;
}

@Component({
  selector: 'app-new-product',
  imports: [ReactiveFormsModule, MatFormFieldModule, MatInputModule, MatIconModule, MatButtonModule, MatIcon],
  templateUrl: './new-product.html',
  styleUrl: './new-product.scss',
})
export class NewProduct implements OnInit {
  private readonly productService = inject(ProductService);
  private readonly mediaService = inject(MediaService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  readonly isEditMode = signal(false);
  private editProductId = '';
  private readonly maxImages = 5;
  private readonly maxImageBytes = 2 * 1024 * 1024;

  readonly isSubmitting = signal(false);
  readonly submitError = signal('');
  readonly fieldErrors = signal<{ [key: string]: string }>({});
  readonly images = signal<ImagePreview[]>([]);
  readonly imageError = signal('');
  readonly isDragging = signal(false);
  private readonly initialEditSignature = signal<string | null>(null);

  readonly productForm = new FormGroup({
    name: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required],
    }),
    description: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required],
    }),
    price: new FormControl<number | null>(null, {
      validators: [Validators.required, Validators.min(0.01)],
    }),
    quantity: new FormControl<number | null>(null, {
      validators: [Validators.required, Validators.min(1)],
    }),
  });

  ngOnInit() {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.isEditMode.set(true);
      this.editProductId = id;
      this.productService.getSingleProduct(id).subscribe((product: Product) => {
        this.productForm.patchValue({
          name: product.name,
          description: product.description,
          price: product.price,
          quantity: product.quantity,
        });
        this.loadExistingImages(product);
      });
    }
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files) {
      this.addFiles(Array.from(input.files));
      input.value = '';
    }
  }

  onDragOver(event: DragEvent): void {
    event.preventDefault();
    this.isDragging.set(true);
  }

  onDragLeave(event: DragEvent): void {
    event.preventDefault();
    this.isDragging.set(false);
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    this.isDragging.set(false);
    if (event.dataTransfer?.files) {
      this.addFiles(Array.from(event.dataTransfer.files));
    }
  }

  removeImage(index: number): void {
    const updated = this.images().filter((_, i) => i !== index);
    this.images.set(updated);
    this.imageError.set('');
  }

  onSubmit(): void {
    this.fieldErrors.set({});
    this.submitError.set('');

    if (this.productForm.invalid) {
      return;
    }
    if (this.isEditMode() && !this.hasUnsavedChanges()) {
      return;
    }

    const formValue = this.productForm.getRawValue();

    this.isSubmitting.set(true);

    if (this.isEditMode()) {
      const basePayload = {
        name: formValue.name,
        description: formValue.description,
        price: formValue.price!,
        quantity: formValue.quantity!,
      };

      this.resolveEditMediaIds()
        .pipe(
          switchMap((mediaIds) =>
            this.productService.updateProduct(this.editProductId, { ...basePayload, mediaIds })),
          finalize(() => this.isSubmitting.set(false)),
        )
        .subscribe({
          next: () => {
            void this.router.navigateByUrl('/seller');
          },
          error: (error: HttpErrorResponse) => this.handleSubmitError(error),
        });
    } else {
      const payload = {
        name: formValue.name,
        description: formValue.description,
        price: formValue.price!,
        quantity: formValue.quantity!,
        mediaIds: [] as string[],
      };

      this.productService.createProduct(payload)
        .pipe(
          switchMap((createdProduct) => {
            const files = this.images().filter((image) => image.file).map((image) => image.file!);
            if (!files.length) {
              return of(createdProduct);
            }

            return this.mediaService.uploadProductImages(createdProduct.id, files).pipe(
              switchMap((uploadResponse) =>
                this.productService.updateProduct(createdProduct.id, {
                  mediaIds: uploadResponse.images.map((image) => image.id),
                }),
              ),
            );
          }),
          finalize(() => this.isSubmitting.set(false)),
        )
        .subscribe({
          next: () => {
            void this.router.navigateByUrl('/seller');
          },
          error: (error: HttpErrorResponse) => this.handleSubmitError(error),
        });
    }
  }

  cancel(): void {
    void this.router.navigateByUrl('/seller');
  }

  getErrorMessage(fieldName: string): string {
    const backendError = this.fieldErrors()[fieldName];
    if (backendError) {
      return backendError;
    }

    const field = this.productForm.get(fieldName);
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
    if (errors['min']) {
      return `${fieldName} must be greater than ${errors['min'].min}`;
    }

    return '';
  }

  isSubmitDisabled(): boolean {
    if (this.isSubmitting() || this.productForm.invalid) {
      return true;
    }

    if (!this.isEditMode()) {
      return false;
    }

    return !this.hasUnsavedChanges();
  }

  private addFiles(files: File[]): void {
    this.imageError.set('');

    const allowedTypes = new Set(['image/png', 'image/jpeg', 'image/gif', 'image/webp']);
    const currentImages = this.images();
    const slotsLeft = this.maxImages - currentImages.length;

    if (slotsLeft <= 0) {
      this.imageError.set('Maximum 5 images are allowed.');
      return;
    }

    const oversized = files.some((file) => file.size > this.maxImageBytes);
    const invalidType = files.some((file) => !allowedTypes.has(file.type));
    const validFiles = files
      .filter((file) => allowedTypes.has(file.type) && file.size <= this.maxImageBytes)
      .slice(0, slotsLeft);

    if (oversized) {
      this.imageError.set('Image size limit exceeded. Each image must be 2 MB or less. Oversized images were not added.');
    } else if (invalidType) {
      this.imageError.set('Only PNG/JPG/GIF/WEBP images up to 2MB are accepted. Unsupported files were not added.');
    } else if (files.length > slotsLeft) {
      this.imageError.set('Maximum 5 images are allowed. Extra images were not added. You can save with the selected images.');
    }

    const newPreviews = validFiles.map((file) => ({
      file,
      url: URL.createObjectURL(file),
    }));

    this.images.set([...currentImages, ...newPreviews]);
  }

  private loadExistingImages(product: Product): void {
    this.mediaService.getProductImages(product.id).subscribe({
      next: (response) => {
        const existingImages: ImagePreview[] = this.mediaService
          .getOrderedProductImages(response, product.mediaIds)
          .map((image) => ({
            file: null,
            url: image.url,
            mediaId: image.id,
          }));

        this.images.set(existingImages);
        this.captureInitialEditState(product, existingImages);
      },
      error: () => {
        this.images.set([]);
        this.captureInitialEditState(product, []);
      },
    });
  }

  private resolveEditMediaIds(): Observable<string[]> {
    const existingMediaIds = this.getCurrentReferencedMediaIds();

    const newFiles = this.images()
      .filter((image) => image.file)
      .map((image) => image.file!) as File[];

    if (!newFiles.length) {
      return of(existingMediaIds);
    }

    return this.mediaService.uploadProductImages(this.editProductId, newFiles).pipe(
      map((uploadResponse) => [
        ...existingMediaIds,
        ...uploadResponse.images.map((image) => image.id),
      ]),
    );
  }

  private handleSubmitError(error: HttpErrorResponse): void {
    if (error.status === 413) {
      this.submitError.set(this.submitError()
        || 'Image size limit exceeded. Use at most 5 images of 2 MB each, then try again.');
      return;
    }

    if (error.error?.details) {
      const details = error.error.details;
      this.fieldErrors.set(details);

      Object.keys(details).forEach((fieldName) => {
        const control = this.productForm.get(fieldName);
        if (control) {
          control.setErrors({ backend: details[fieldName] });
          control.markAsTouched();
        }
      });
      return;
    }

    if (error.error?.message) {
      this.submitError.set(error.error.message);
      return;
    }

    if (error.status === 403) {
      this.submitError.set('Seller permission is required. Please sign in again with a seller account.');
      return;
    }

    this.submitError.set(this.isEditMode() ? 'Failed to update product' : 'Failed to create product');
  }

  private hasUnsavedChanges(): boolean {
    const initialSignature = this.initialEditSignature();
    if (!initialSignature) {
      return false;
    }

    if (this.images().some((image) => !!image.file)) {
      return true;
    }

    const formValue = this.productForm.getRawValue();
    const currentSignature = this.toStateSignature(
      formValue.name,
      formValue.description,
      formValue.price,
      formValue.quantity,
      this.getCurrentReferencedMediaIds(),
    );

    return currentSignature !== initialSignature;
  }

  private captureInitialEditState(product: Product, existingImages: ImagePreview[]): void {
    const initialMediaIds = existingImages
      .filter((image) => image.mediaId)
      .map((image) => image.mediaId as string);

    this.initialEditSignature.set(
      this.toStateSignature(
        product.name,
        product.description,
        product.price,
        product.quantity,
        initialMediaIds,
      ),
    );
  }

  private getCurrentReferencedMediaIds(): string[] {
    return this.images()
      .filter((image) => image.mediaId)
      .map((image) => image.mediaId as string);
  }

  private toStateSignature(
    name: string,
    description: string,
    price: number | null,
    quantity: number | null,
    mediaIds: string[],
  ): string {
    return JSON.stringify({
      name,
      description,
      price,
      quantity,
      mediaIds,
    });
  }
}
