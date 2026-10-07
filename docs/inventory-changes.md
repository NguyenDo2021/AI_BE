# File thay đổi: quản lý kho và danh mục vật tư

Các thay đổi xóa `src/main/java/com/frontendbase/api/qlHoSo/` đã có trước khi triển khai được giữ nguyên và không thuộc danh sách thay đổi tính năng dưới đây. Không sửa migration V1–V4, API đăng nhập hoặc service user/role/permission hiện có.

Tổng cộng 43 file bổ sung/sửa:

- [README.md](../README.md)
- [docs/inventory-api.md](../docs/inventory-api.md)
- [docs/inventory-changes.md](../docs/inventory-changes.md)
- [src/main/java/com/frontendbase/api/common/PermissionCodes.java](../src/main/java/com/frontendbase/api/common/PermissionCodes.java)
- [src/main/java/com/frontendbase/api/common/validation/StrictIntegerDeserializer.java](../src/main/java/com/frontendbase/api/common/validation/StrictIntegerDeserializer.java)
- [src/main/java/com/frontendbase/api/product/controller/ProductController.java](../src/main/java/com/frontendbase/api/product/controller/ProductController.java)
- [src/main/java/com/frontendbase/api/product/dto/ProductPageResponse.java](../src/main/java/com/frontendbase/api/product/dto/ProductPageResponse.java)
- [src/main/java/com/frontendbase/api/product/dto/ProductPayload.java](../src/main/java/com/frontendbase/api/product/dto/ProductPayload.java)
- [src/main/java/com/frontendbase/api/product/dto/ProductResponse.java](../src/main/java/com/frontendbase/api/product/dto/ProductResponse.java)
- [src/main/java/com/frontendbase/api/product/entity/Product.java](../src/main/java/com/frontendbase/api/product/entity/Product.java)
- [src/main/java/com/frontendbase/api/product/mapper/ProductMapper.java](../src/main/java/com/frontendbase/api/product/mapper/ProductMapper.java)
- [src/main/java/com/frontendbase/api/product/repository/ProductRepository.java](../src/main/java/com/frontendbase/api/product/repository/ProductRepository.java)
- [src/main/java/com/frontendbase/api/product/service/ProductService.java](../src/main/java/com/frontendbase/api/product/service/ProductService.java)
- [src/main/java/com/frontendbase/api/product/service/ProductSpecifications.java](../src/main/java/com/frontendbase/api/product/service/ProductSpecifications.java)
- [src/main/java/com/frontendbase/api/productGroup/controller/ProductGroupController.java](../src/main/java/com/frontendbase/api/productGroup/controller/ProductGroupController.java)
- [src/main/java/com/frontendbase/api/productGroup/dto/ProductGroupPageResponse.java](../src/main/java/com/frontendbase/api/productGroup/dto/ProductGroupPageResponse.java)
- [src/main/java/com/frontendbase/api/productGroup/dto/ProductGroupPayload.java](../src/main/java/com/frontendbase/api/productGroup/dto/ProductGroupPayload.java)
- [src/main/java/com/frontendbase/api/productGroup/dto/ProductGroupResponse.java](../src/main/java/com/frontendbase/api/productGroup/dto/ProductGroupResponse.java)
- [src/main/java/com/frontendbase/api/productGroup/entity/ProductGroup.java](../src/main/java/com/frontendbase/api/productGroup/entity/ProductGroup.java)
- [src/main/java/com/frontendbase/api/productGroup/mapper/ProductGroupMapper.java](../src/main/java/com/frontendbase/api/productGroup/mapper/ProductGroupMapper.java)
- [src/main/java/com/frontendbase/api/productGroup/repository/ProductGroupRepository.java](../src/main/java/com/frontendbase/api/productGroup/repository/ProductGroupRepository.java)
- [src/main/java/com/frontendbase/api/productGroup/service/ProductGroupService.java](../src/main/java/com/frontendbase/api/productGroup/service/ProductGroupService.java)
- [src/main/java/com/frontendbase/api/productGroup/service/ProductGroupSpecifications.java](../src/main/java/com/frontendbase/api/productGroup/service/ProductGroupSpecifications.java)
- [src/main/java/com/frontendbase/api/user/repository/UserRepository.java](../src/main/java/com/frontendbase/api/user/repository/UserRepository.java)
- [src/main/java/com/frontendbase/api/warehouse/controller/UserWarehouseController.java](../src/main/java/com/frontendbase/api/warehouse/controller/UserWarehouseController.java)
- [src/main/java/com/frontendbase/api/warehouse/controller/WarehouseController.java](../src/main/java/com/frontendbase/api/warehouse/controller/WarehouseController.java)
- [src/main/java/com/frontendbase/api/warehouse/dto/WarehousePageResponse.java](../src/main/java/com/frontendbase/api/warehouse/dto/WarehousePageResponse.java)
- [src/main/java/com/frontendbase/api/warehouse/dto/WarehousePayload.java](../src/main/java/com/frontendbase/api/warehouse/dto/WarehousePayload.java)
- [src/main/java/com/frontendbase/api/warehouse/dto/WarehouseResponse.java](../src/main/java/com/frontendbase/api/warehouse/dto/WarehouseResponse.java)
- [src/main/java/com/frontendbase/api/warehouse/entity/UserWarehouse.java](../src/main/java/com/frontendbase/api/warehouse/entity/UserWarehouse.java)
- [src/main/java/com/frontendbase/api/warehouse/entity/UserWarehouseId.java](../src/main/java/com/frontendbase/api/warehouse/entity/UserWarehouseId.java)
- [src/main/java/com/frontendbase/api/warehouse/entity/Warehouse.java](../src/main/java/com/frontendbase/api/warehouse/entity/Warehouse.java)
- [src/main/java/com/frontendbase/api/warehouse/mapper/WarehouseMapper.java](../src/main/java/com/frontendbase/api/warehouse/mapper/WarehouseMapper.java)
- [src/main/java/com/frontendbase/api/warehouse/repository/UserWarehouseRepository.java](../src/main/java/com/frontendbase/api/warehouse/repository/UserWarehouseRepository.java)
- [src/main/java/com/frontendbase/api/warehouse/repository/WarehouseRepository.java](../src/main/java/com/frontendbase/api/warehouse/repository/WarehouseRepository.java)
- [src/main/java/com/frontendbase/api/warehouse/service/UserWarehouseService.java](../src/main/java/com/frontendbase/api/warehouse/service/UserWarehouseService.java)
- [src/main/java/com/frontendbase/api/warehouse/service/WarehouseAccess.java](../src/main/java/com/frontendbase/api/warehouse/service/WarehouseAccess.java)
- [src/main/java/com/frontendbase/api/warehouse/service/WarehouseService.java](../src/main/java/com/frontendbase/api/warehouse/service/WarehouseService.java)
- [src/main/java/com/frontendbase/api/warehouse/service/WarehouseSpecifications.java](../src/main/java/com/frontendbase/api/warehouse/service/WarehouseSpecifications.java)
- [src/main/resources/db/migration/V5__add_warehouse_and_product_catalog.sql](../src/main/resources/db/migration/V5__add_warehouse_and_product_catalog.sql)
- [src/test/java/com/frontendbase/api/AuthAndUserApiIntegrationTest.java](../src/test/java/com/frontendbase/api/AuthAndUserApiIntegrationTest.java)
- [src/test/java/com/frontendbase/api/H2MigrationConfiguration.java](../src/test/java/com/frontendbase/api/H2MigrationConfiguration.java)
- [src/test/java/com/frontendbase/api/InventoryApiIntegrationTest.java](../src/test/java/com/frontendbase/api/InventoryApiIntegrationTest.java)

## Giải thích các phần sửa sẵn có

- `PermissionCodes.java`: bổ sung 11 mã quyền mới vào constants/ALL.
- `UserRepository.java`: thêm truy vấn khóa user để serialize gán/gỡ kho đồng thời.
- `AuthAndUserApiIntegrationTest.java`: import adapter H2 và chỉnh các fixture không khớp code hiện tại; không đổi chức năng API cũ.
- `README.md`: liên kết tới tài liệu API mới.

Xem [API và hướng dẫn kiểm tra](inventory-api.md) để biết method/URL/quyền/payload/response/lỗi, migration, giả định và kết quả test.
