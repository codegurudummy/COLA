package com.alibaba.cola.command;

import com.alibaba.cola.exception.*;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import com.alibaba.cola.dto.Command;
import com.alibaba.cola.dto.Response;
import com.alibaba.cola.logger.Logger;
import com.alibaba.cola.logger.LoggerFactory;
/**
 * Just send Command to CommandBus, 
 * 
 * @author fulan.zjf 2017年10月24日 上午12:47:18
 */
@Component
public class CommandBus implements CommandBusI, ApplicationContextAware{
    
    Logger logger = LoggerFactory.getLogger(CommandBus.class);
    
    @Autowired
    private CommandHub commandHub;

    private ApplicationContext applicationContext;

    @SuppressWarnings("unchecked")
    @Override
    public Response send(Command cmd) {
        Response response = null;
        try {
            response = commandHub.getCommandInvocation(cmd.getClass()).invoke(cmd);
        }
        catch (Exception exception) {
            response = handleException(cmd, exception);
        }
        return response;
    }

    private Response handleException(Command cmd, Exception exception) {
        Response response = getResponseInstance(cmd);

        ExceptionHandlerI exceptionHandler = getCustomerizedExceptionHandler();
        if (exceptionHandler != null){
            exceptionHandler.handleException(cmd, response, exception);
            return response;
        }

        defaultHandleException(cmd, response, exception);
        return response;
    }

    private Response getResponseInstance(Command cmd) {
        Class responseClz = commandHub.getResponseRepository().get(cmd.getClass());
        try {
            return (Response) responseClz.newInstance();
        } catch (Exception e) {
            logger.error("Process "+cmd+" error: "+e.getMessage(), e);
            throw new ColaException(e.getMessage());
        }
    }

    private void defaultHandleException(Command cmd, Response response, Exception exception) {
        if (exception instanceof AppException) {
            ErrorCodeI errCode = ((AppException) exception).getErrCode();
            response.setErrCode(errCode.getErrCode());
        }
        else {
            response.setErrCode(BasicErrorCode.S_UNKNOWN.getErrCode());
        }
        response.setErrMessage(exception.getMessage());
        logger.error("Process ["+cmd+"] error, errorCode: "
                + response.getErrCode() + " errorMsg:"
                + response.getErrMessage(), exception);
    }

    private ExceptionHandlerI getCustomerizedExceptionHandler() {
        try {
            return applicationContext.getBean(ExceptionHandlerI.class);
        }
        catch (NoSuchBeanDefinitionException ex){
            return null;
        }
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}
