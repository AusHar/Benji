package com.austinharlan.trading_dashboard.controllers;

import com.austinharlan.trading_dashboard.service.ImportService;
import com.austinharlan.tradingdashboard.api.ImportApi;
import com.austinharlan.tradingdashboard.dto.ImportConfirmResponse;
import com.austinharlan.tradingdashboard.dto.ImportPreviewResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class ImportController implements ImportApi {

  private final ImportService importService;

  public ImportController(ImportService importService) {
    this.importService = importService;
  }

  @Override
  public ResponseEntity<ImportPreviewResponse> previewCsvImport(
      MultipartFile file, String account) {
    return ResponseEntity.ok(importService.preview(file, account));
  }

  @Override
  public ResponseEntity<ImportConfirmResponse> confirmCsvImport(
      MultipartFile file, String account) {
    return ResponseEntity.ok(importService.confirm(file, account));
  }
}
