package com.project.demo.common.exception.portfolio;

import com.project.demo.common.exception.BusinessException;
import com.project.demo.common.exception.ErrorCode;

public class NotFoundPortfolioException extends BusinessException {
    public NotFoundPortfolioException(){super(ErrorCode.PORTFOLIO_NOT_FOUND);}
}
