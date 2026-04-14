package com.austinharlan.trading_dashboard.service;

import com.austinharlan.tradingdashboard.dto.ImportConfirmResponse;
import com.austinharlan.tradingdashboard.dto.ImportPreviewResponse;
import org.springframework.web.multipart.MultipartFile;

public interface ImportService {

  ImportPreviewResponse preview(MultipartFile file, String account);

  ImportConfirmResponse confirm(MultipartFile file, String account);
}
