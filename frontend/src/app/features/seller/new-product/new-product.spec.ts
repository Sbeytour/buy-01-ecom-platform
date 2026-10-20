import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ANIMATION_MODULE_TYPE } from '@angular/platform-browser/animations';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';

import { NewProduct } from './new-product';
import { ProductService } from '../../../core/services/product.service';
import { MediaService } from '../../../core/services/media.service';
import { Product } from '../../../core/models/product';
import { MediaImage, ProductImagesResponse } from '../../../core/models/media';

describe('NewProduct Component', () => {
  let component: NewProduct;
  let fixture: ComponentFixture<NewProduct>;
  let productServiceSpy: jasmine.SpyObj<ProductService>;
  let mediaServiceSpy: jasmine.SpyObj<MediaService>;
  let routerSpy: jasmine.SpyObj<Router>;
  let activatedRouteStub: any;

  const mockProduct: Product = {
    id: 'prod-123',
    name: 'Test Product',
    description: 'A great product',
    price: 99.99,
    quantity: 10,
    mediaIds: ['media-1'],
    sellerId: 'seller-1',
    createdAt: new Date(),
    updatedAt: new Date(),
  };

  const mockMediaImages: MediaImage[] = [
    { id: 'media-1', url: 'http://example.com/image.jpg' },
  ];

  const mockImagesResponse: ProductImagesResponse = {
    productId: 'prod-123',
    images: mockMediaImages,
  };

  beforeEach(async () => {
    productServiceSpy = jasmine.createSpyObj('ProductService', [
      'createProduct',
      'deleteProduct',
      'updateProduct',
      'getSingleProduct',
    ]);
    mediaServiceSpy = jasmine.createSpyObj('MediaService', [
      'uploadProductImages',
      'getProductImages',
      'getOrderedProductImages',
    ]);
    routerSpy = jasmine.createSpyObj('Router', ['navigateByUrl']);

    activatedRouteStub = {
      snapshot: {
        paramMap: {
          get: jasmine.createSpy('get').and.returnValue(null), // Defaults to Create mode
        },
      },
    };

    // Mock URL.createObjectURL to prevent issues in the test environment
    spyOn(URL, 'createObjectURL').and.returnValue('blob:mock-url');

    await TestBed.configureTestingModule({
      imports: [NewProduct],
      providers: [
        { provide: ProductService, useValue: productServiceSpy },
        { provide: MediaService, useValue: mediaServiceSpy },
        { provide: Router, useValue: routerSpy },
        { provide: ActivatedRoute, useValue: activatedRouteStub },
        { provide: ANIMATION_MODULE_TYPE, useValue: 'NoopAnimations' },
      ],
    }).compileComponents();
  });

  describe('Create Mode', () => {
    beforeEach(() => {
      fixture = TestBed.createComponent(NewProduct);
      component = fixture.componentInstance;
      fixture.detectChanges(); // Triggers ngOnInit in Create mode
    });

    it('should initialize with an empty, invalid form', () => {
      expect(component.isEditMode()).toBeFalse();
      expect(component.productForm.invalid).toBeTrue();
      expect(component.images().length).toBe(0);
    });

    it('should validate minimum price and quantity', () => {
      const { price, quantity } = component.productForm.controls;

      price.setValue(0);
      expect(price.hasError('min')).toBeTrue();
      price.setValue(0.01);
      expect(price.errors).toBeNull();

      quantity.setValue(0);
      expect(quantity.hasError('min')).toBeTrue();
      quantity.setValue(1);
      expect(quantity.errors).toBeNull();
    });

    describe('Image Handling', () => {
      it('should add valid image files and generate previews', () => {
        const mockFile = new File([''], 'test.png', { type: 'image/png' });
        const event = { target: { files: [mockFile] } } as unknown as Event;

        component.onFileSelected(event);

        expect(component.images().length).toBe(1);
        expect(component.images()[0].file).toBe(mockFile);
        expect(component.images()[0].url).toBe('blob:mock-url');
        expect(component.imageError()).toBe('');
      });

      it('should reject invalid file types and sizes', () => {
        const textFile = new File([''], 'test.txt', { type: 'text/plain' });

        // Artificially create a large file
        const largeFile = new File([''], 'large.jpg', { type: 'image/jpeg' });
        Object.defineProperty(largeFile, 'size', { value: 3 * 1024 * 1024 });

        const event = { target: { files: [textFile, largeFile] } } as unknown as Event;
        component.onFileSelected(event);

        expect(component.images().length).toBe(0);
        expect(component.imageError()).toContain('Image size limit exceeded');
      });

      it('should enforce a maximum of 5 images', () => {
        const files = Array.from({ length: 6 }).map((_, i) => new File([''], `test${i}.png`, { type: 'image/png' }));
        const event = { target: { files } } as unknown as Event;

        component.onFileSelected(event);

        expect(component.images().length).toBe(5);
        expect(component.imageError()).toContain('Maximum 5 images');
      });

      it('should remove an image by index', () => {
        const file = new File([''], 'test.png', { type: 'image/png' });
        component.onFileSelected({ target: { files: [file] } } as unknown as Event);

        expect(component.images().length).toBe(1);
        component.removeImage(0);
        expect(component.images().length).toBe(0);
      });
    });

    describe('Submission', () => {
      const validFormPayload = {
        name: 'New Item',
        description: 'Details',
        price: 15.0,
        quantity: 5,
      };

      beforeEach(() => {
        component.productForm.setValue(validFormPayload);
      });

      it('should submit product data without images successfully', () => {
        productServiceSpy.createProduct.and.returnValue(of(mockProduct));

        component.onSubmit();

        expect(productServiceSpy.createProduct).toHaveBeenCalledWith({
          ...validFormPayload,
          mediaIds: [],
        });
        expect(mediaServiceSpy.uploadProductImages).not.toHaveBeenCalled();
        expect(routerSpy.navigateByUrl).toHaveBeenCalledWith('/seller');
      });

      it('should chain image upload and product update when images are provided', () => {
        productServiceSpy.createProduct.and.returnValue(of(mockProduct));
        mediaServiceSpy.uploadProductImages.and.returnValue(of(mockImagesResponse));
        productServiceSpy.updateProduct.and.returnValue(of(mockProduct));

        // Add a mock image
        const mockFile = new File([''], 'test.png', { type: 'image/png' });
        component.onFileSelected({ target: { files: [mockFile] } } as unknown as Event);

        component.onSubmit();

        expect(productServiceSpy.createProduct).toHaveBeenCalled();
        expect(mediaServiceSpy.uploadProductImages).toHaveBeenCalledWith('prod-123', [mockFile]);
        expect(productServiceSpy.updateProduct).toHaveBeenCalledWith('prod-123', { mediaIds: ['media-1'] });
        expect(routerSpy.navigateByUrl).toHaveBeenCalledWith('/seller');
        expect(component.isSubmitting()).toBeFalse();
      });

      it('should stop creation when an oversized image is selected', () => {
        const file = new File(['image'], 'large.png', { type: 'image/png' });
        Object.defineProperty(file, 'size', { value: 2 * 1024 * 1024 + 1 });
        component.onFileSelected({ target: { files: [file] } } as unknown as Event);
        component.onSubmit();
        expect(component.submitError()).toContain('Image size limit exceeded');
        expect(productServiceSpy.createProduct).not.toHaveBeenCalled();
        expect(mediaServiceSpy.uploadProductImages).not.toHaveBeenCalled();
      });

      it('should submit four valid images when a fifth oversized image is rejected', () => {
        const valid = Array.from({ length: 4 }, (_, i) =>
          new File(['image'], `valid${i}.png`, { type: 'image/png' }));
        const oversized = new File(['image'], 'large.png', { type: 'image/png' });
        Object.defineProperty(oversized, 'size', { value: 3 * 1024 * 1024 });
        component.onFileSelected({ target: { files: [...valid, oversized] } } as unknown as Event);
        expect(component.images().length).toBe(4);
        expect(component.imageError()).toContain('Oversized images were not added');
        productServiceSpy.createProduct.and.returnValue(of(mockProduct));
        mediaServiceSpy.uploadProductImages.and.returnValue(of(mockImagesResponse));
        productServiceSpy.updateProduct.and.returnValue(of(mockProduct));
        component.onSubmit();
        expect(productServiceSpy.createProduct).toHaveBeenCalledTimes(1);
        expect(mediaServiceSpy.uploadProductImages).toHaveBeenCalledWith('prod-123', valid);
        expect(component.submitError()).toBe('');
        expect(routerSpy.navigateByUrl).toHaveBeenCalledWith('/seller');
      });

      it('should preserve valid previews and permit submission after a later oversized selection', () => {
        const valid = new File(['image'], 'valid.png', { type: 'image/png' });
        component.onFileSelected({ target: { files: [valid] } } as unknown as Event);
        const oversized = new File(['image'], 'large.png', { type: 'image/png' });
        Object.defineProperty(oversized, 'size', { value: 3 * 1024 * 1024 });
        component.onFileSelected({ target: { files: [oversized] } } as unknown as Event);
        productServiceSpy.createProduct.and.returnValue(of(mockProduct));
        mediaServiceSpy.uploadProductImages.and.returnValue(of(mockImagesResponse));
        productServiceSpy.updateProduct.and.returnValue(of(mockProduct));
        component.onSubmit();
        expect(mediaServiceSpy.uploadProductImages).toHaveBeenCalledWith('prod-123', [valid]);
        expect(component.submitError()).toBe('');
      });

      it('should allow correction after rejecting an oversized image', () => {
        const large = new File(['image'], 'large.png', { type: 'image/png' });
        Object.defineProperty(large, 'size', { value: 3 * 1024 * 1024 });
        component.onFileSelected({ target: { files: [large] } } as unknown as Event);
        const valid = new File(['image'], 'valid.png', { type: 'image/png' });
        component.onFileSelected({ target: { files: [valid] } } as unknown as Event);
        productServiceSpy.createProduct.and.returnValue(of(mockProduct));
        mediaServiceSpy.uploadProductImages.and.returnValue(of(mockImagesResponse));
        productServiceSpy.updateProduct.and.returnValue(of(mockProduct));
        component.onSubmit();
        expect(productServiceSpy.createProduct).toHaveBeenCalledTimes(1);
        expect(mediaServiceSpy.uploadProductImages).toHaveBeenCalledWith('prod-123', [valid]);
      });

      it('should reject the sixth image and still submit five images at the size boundary', () => {
        const files = Array.from({ length: 6 }, (_, i) => {
          const file = new File(['image'], `image${i}.png`, { type: 'image/png' });
          Object.defineProperty(file, 'size', { value: 2 * 1024 * 1024 });
          return file;
        });
        component.onFileSelected({ target: { files } } as unknown as Event);
        expect(component.images().length).toBe(5);
        expect(component.imageError()).toContain('Maximum 5 images');
        productServiceSpy.createProduct.and.returnValue(of(mockProduct));
        mediaServiceSpy.uploadProductImages.and.returnValue(of(mockImagesResponse));
        productServiceSpy.updateProduct.and.returnValue(of(mockProduct));
        component.onSubmit();
        expect(mediaServiceSpy.uploadProductImages).toHaveBeenCalledWith('prod-123', files.slice(0, 5));
        expect(routerSpy.navigateByUrl).toHaveBeenCalledWith('/seller');
      });

      it('should delete the newly created draft and explain a proxy 413', () => {
        const file = new File(['image'], 'test.png', { type: 'image/png' });
        component.onFileSelected({ target: { files: [file] } } as unknown as Event);
        productServiceSpy.createProduct.and.returnValue(of(mockProduct));
        productServiceSpy.deleteProduct.and.returnValue(of(undefined));
        mediaServiceSpy.uploadProductImages.and.returnValue(throwError(() =>
          new HttpErrorResponse({ status: 413, error: '<html>Request Entity Too Large</html>' })));
        component.onSubmit();
        expect(productServiceSpy.deleteProduct).toHaveBeenCalledWith('prod-123');
        expect(productServiceSpy.updateProduct).not.toHaveBeenCalled();
        expect(component.submitError()).toContain('Image size limit exceeded');
        expect(component.isSubmitting()).toBeFalse();
        expect(routerSpy.navigateByUrl).not.toHaveBeenCalled();
      });

      it('should explain when a size rejection cannot be cleaned up', () => {
        const file = new File(['image'], 'test.png', { type: 'image/png' });
        component.onFileSelected({ target: { files: [file] } } as unknown as Event);
        productServiceSpy.createProduct.and.returnValue(of(mockProduct));
        productServiceSpy.deleteProduct.and.returnValue(throwError(() => new HttpErrorResponse({ status: 503 })));
        mediaServiceSpy.uploadProductImages.and.returnValue(throwError(() => new HttpErrorResponse({ status: 413 })));
        component.onSubmit();
        expect(component.submitError()).toContain('could not be removed');
        expect(component.submitError()).toContain('Delete it from your dashboard');
        expect(component.isSubmitting()).toBeFalse();
        expect(routerSpy.navigateByUrl).not.toHaveBeenCalled();
      });

      it('should handle 400 backend field errors', () => {
        productServiceSpy.createProduct.and.returnValue(throwError(() => new HttpErrorResponse({
          status: 400,
          error: { details: { name: 'Name must be unique' } }
        })));

        component.onSubmit();

        expect(component.fieldErrors()['name']).toBe('Name must be unique');
        expect(component.productForm.controls.name.hasError('backend')).toBeTrue();
      });

      it('should handle 403 Forbidden errors', () => {
        productServiceSpy.createProduct.and.returnValue(throwError(() => new HttpErrorResponse({
          status: 403,
        })));

        component.onSubmit();

        expect(component.submitError()).toContain('Seller permission is required');
      });
    });
  });

  describe('Edit Mode', () => {
    beforeEach(() => {
      // Setup the route stub to return an ID, simulating an edit route
      activatedRouteStub.snapshot.paramMap.get.and.returnValue('prod-123');

      // Setup mock returns for the initialization calls
      productServiceSpy.getSingleProduct.and.returnValue(of(mockProduct));
      mediaServiceSpy.getProductImages.and.returnValue(of(mockImagesResponse));
      mediaServiceSpy.getOrderedProductImages.and.returnValue(mockMediaImages);

      fixture = TestBed.createComponent(NewProduct);
      component = fixture.componentInstance;
      fixture.detectChanges(); // Triggers ngOnInit
    });

    it('should initialize with existing product data and images', () => {
      expect(component.isEditMode()).toBeTrue();
      expect(productServiceSpy.getSingleProduct).toHaveBeenCalledWith('prod-123');
      expect(mediaServiceSpy.getProductImages).toHaveBeenCalledWith('prod-123');

      // Check form populated
      expect(component.productForm.value).toEqual({
        name: 'Test Product',
        description: 'A great product',
        price: 99.99,
        quantity: 10,
      });

      // Check images populated
      expect(component.images().length).toBe(1);
      expect(component.images()[0].mediaId).toBe('media-1');
      expect(component.images()[0].url).toBe('http://example.com/image.jpg');
      expect(component.images()[0].file).toBeNull(); // Existing images have no File object
    });

    describe('Unsaved Changes Detection', () => {
      it('should disable submit button if no changes are made', () => {
        expect(component.isSubmitDisabled()).toBeTrue();
      });

      it('should enable submit button if form values change', () => {
        component.productForm.patchValue({ name: 'Updated Name' });
        expect(component.isSubmitDisabled()).toBeFalse();
      });

      it('should enable submit button if an existing image is removed', () => {
        component.removeImage(0); // Remove the existing image
        expect(component.isSubmitDisabled()).toBeFalse();
      });

      it('should enable submit button if a new image is added', () => {
        const mockFile = new File([''], 'new.png', { type: 'image/png' });
        component.onFileSelected({ target: { files: [mockFile] } } as unknown as Event);

        expect(component.isSubmitDisabled()).toBeFalse();
      });
    });

    describe('Submission', () => {
      it('should submit updates without new images', () => {
        // Make a change to enable the submit button
        component.productForm.patchValue({ name: 'Updated Product' });

        productServiceSpy.updateProduct.and.returnValue(of(mockProduct));

        component.onSubmit();

        // Should not upload images since none were added
        expect(mediaServiceSpy.uploadProductImages).not.toHaveBeenCalled();

        // Should update product with existing mediaIds preserved
        expect(productServiceSpy.updateProduct).toHaveBeenCalledWith('prod-123', {
          name: 'Updated Product',
          description: 'A great product',
          price: 99.99,
          quantity: 10,
          mediaIds: ['media-1'] // Retained existing mediaId
        });
      });

      it('should upload new images and merge mediaIds on submit', () => {
        // Add a new image
        const newFile = new File([''], 'new.png', { type: 'image/png' });
        component.onFileSelected({ target: { files: [newFile] } } as unknown as Event);

        const newImagesResponse: ProductImagesResponse = {
          productId: 'prod-123',
          images: [{ id: 'media-2', url: 'http://example.com/new.jpg' }]
        };

        mediaServiceSpy.uploadProductImages.and.returnValue(of(newImagesResponse));
        productServiceSpy.updateProduct.and.returnValue(of(mockProduct));

        component.onSubmit();

        // Verify only the new file was uploaded
        expect(mediaServiceSpy.uploadProductImages).toHaveBeenCalledWith('prod-123', [newFile]);

        // Verify the update payload merged old ('media-1') and new ('media-2') ids
        expect(productServiceSpy.updateProduct).toHaveBeenCalledWith('prod-123', {
          name: 'Test Product',
          description: 'A great product',
          price: 99.99,
          quantity: 10,
          mediaIds: ['media-1', 'media-2']
        });
      });
    });
  });
});