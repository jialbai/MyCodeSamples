package com.sirp.platform.sirpapi;

import com.alibaba.fastjson.JSON;
import com.sirp.platform.admin.base.controller.BaseController;
import com.sirp.platform.common.constant.SpConstant;
import com.sirp.platform.common.dto.MonitorModel;
import com.sirp.platform.common.enums.ErrorEnum;
import com.sirp.platform.common.exception.BusinessException;
import com.sirp.platform.common.exception.RedisBusinessException;
import com.sirp.platform.common.utils.StringHelper;
import com.sirp.platform.core.redis.JedisManager;
import com.sirp.platform.sirpapi.base.BaseRo;
import com.sirp.platform.sirpapi.base.companymodel.org.CompanyOrgBuyerModel;
import com.sirp.platform.sirpapi.base.companymodel.visibility.CompanyBuyerVisibilityModel;
import com.sirp.platform.sirpapi.configs.service.ConfigsService;
import com.sirp.platform.sirpapi.org.service.OrgService;
import com.sirp.platform.sirpapi.org.vo.*;
import com.sirp.platform.sirpapi.org.vo.returndata.VisibleBuyerInfosReturnData;
import com.sirp.platform.sirpapi.persistence.SpSysCompanySuperuserRefMapper;
import com.sirp.platform.sirpapi.upload.service.UploadService;
import com.sirp.platform.sirpapi.users.service.UsersService;
import com.sirp.platform.sirpapi.users.vo.model.SpBuyerUserModel;
import com.sirp.platform.sirpapi.users.vo.model.SpCompanyBuyerUserModel;
import com.sirp.platform.system.dto.SysUser;
import com.sirp.platform.system.service.AuthorizationService;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Controller
@RequestMapping("/org")
public class OrganizationController extends BaseController {
    protected Logger logger = LoggerFactory.getLogger(getClass());

    @Autowired
    private ConfigsService configsService;
    @Autowired
    private OrgService orgService;
    @Autowired
    private SpSysCompanySuperuserRefMapper companySuperuserRefDao;
    @Autowired
    private UsersService usersService;
    @Autowired
    private AuthorizationService authorizationService;
    @Autowired
    private UploadService uploadService;
    @Autowired
    private JedisManager jedisManager;

    @CrossOrigin(origins = "*")
    @RequiresPermissions("sp:org:refreshCompanyOrgModel")
    @RequestMapping(value = "/refreshCompanyOrgModel", method = RequestMethod.GET)
    @ResponseBody
    public BaseRo refreshCompanyOrgModel() {
        logger.info("ENTER [/org/refreshCompanyOrgModel]");
//        logger.info("Request content[{}]", JSON.toJSONString(reqVo));
        long start = System.currentTimeMillis();
        GetCompanyOrgModelResVo resVo = new GetCompanyOrgModelResVo();
        try {
            // 1. param validation
//            if (result.hasErrors()) {
//                ObjectError objectError = result.getAllErrors().get(0);
//                logger.info(objectError.getDefaultMessage());
//                resVo.setMsg(ErrorEnum.RES_0002.getErrorMsg());
//                resVo.setState(ErrorEnum.RES_0002.getErrorCode());
//                return resVo;
//            }
            // 2. verify signature if needed
            // 3. business logic
            // session中获取用户信息
            SysUser currentUser = super.getCurrentUser();
            Integer userId = currentUser.getId();
            String userType = usersService.getUserTypeByUserId(currentUser.getId());
            if (userId == null) {
                logger.error(String.format("No login user bound with session id"));
                resVo.setState(ErrorEnum.RES_0002.getErrorCode());
                resVo.setMsg(ErrorEnum.RES_0002.getErrorMsg());
            } else {
                if (SpConstant.USER_TYPE.CORP_BUYER.equals(userType)) {
                    // find user info
                    SpCompanyBuyerUserModel buyerUserModel = (SpCompanyBuyerUserModel) usersService.findCommonUserModelByUserId(userId);
                    if (buyerUserModel != null) {
                        // 拿到OrgModel 不从redis拿 直接生成并推入redis
                        CompanyOrgBuyerModel buyerModel = (CompanyOrgBuyerModel) orgService.refreshRedisCompanyModelBySysCompanyId(buyerUserModel.getSysCompanyId(), buyerUserModel.getOrgType());
                        resVo.setOrgBuyerModel(buyerModel);
                        resVo.setState(ErrorEnum.RES_0000.getErrorCode());
                        resVo.setMsg(ErrorEnum.RES_0000.getErrorMsg());
                    } else {
                        logger.error(String.format("User info missing!"));
                        resVo.setState(ErrorEnum.RES_0002.getErrorCode());
                        resVo.setMsg(ErrorEnum.RES_0002.getErrorMsg() + " User info missing!");
                    }
                } else {
                    logger.error(String.format("User type %s is not supported!", userType));
                    resVo.setState(ErrorEnum.RES_0002.getErrorCode());
                    resVo.setMsg(ErrorEnum.RES_0002.getErrorMsg() + " Invalid user type!");
                }
            }
        } catch (RedisBusinessException e) {
            // error push model to redis, but no influence to business logic
            logger.error("[/org/refreshCompanyOrgModel]Request exception," + e.getMessage(), e);
            resVo.setState(ErrorEnum.RES_0000.getErrorCode());
            resVo.setMsg(ErrorEnum.RES_0000.getErrorMsg());
        } catch (BusinessException e) {
            logger.error("[/org/refreshCompanyOrgModel]Request exception," + e.getMessage(), e);
            resVo.setState(ErrorEnum.RES_0002.getErrorCode());
            resVo.setMsg(ErrorEnum.RES_0002.getErrorMsg() + e.getMessage());
        } catch (BeansException be) {
            logger.error("[/org/refreshCompanyOrgModel]Exception when copying beans," + be.getMessage(), be);
            resVo.setState(ErrorEnum.RES_0009.getErrorCode());
            resVo.setMsg(ErrorEnum.RES_0009.getErrorMsg() + be.getMessage());
        } catch (Exception e) {
            logger.error("[/org/refreshCompanyOrgModel]Unknown exception," + e.getMessage(), e);
            resVo.setState(ErrorEnum.RES_9999.getErrorCode());
            resVo.setMsg(ErrorEnum.RES_9999.getErrorMsg() + e.getMessage());

            MonitorModel monitorModel = new MonitorModel(ErrorEnum.RES_9999.getErrorCode(),
                    "/org/refreshCompanyOrgModel", JSON.toJSONString(null),
                    StringHelper.getNthLine(e.getMessage(), 10), JSON.toJSONString(super.getCurrentUser()), "Exception", SpConstant.MONITORING.PRIORITY.LEVEL2);
            Date date = new Date();
            jedisManager.setValueByKey(SpConstant.MONITORING.PREFIX.MONITOR_PREFIX +
                            String.valueOf(date.getTime()) +
                            "refreshCompanyOrgModel",
                    JSON.toJSONString(monitorModel), 86400);

        } finally {
            long costTime = System.currentTimeMillis() - start;
            logger.info("[/org/refreshCompanyOrgModel]Response content[{}], cost {} ms", JSON.toJSONString(resVo), costTime);
            return resVo;
        }
    }

    @CrossOrigin(origins = "*")
    @RequiresPermissions("sp:org:asyncRefreshCompanyOrgModel")
    @RequestMapping(value = "/asyncRefreshCompanyOrgModel", method = RequestMethod.GET)
    @ResponseBody
    public BaseRo asyncRefreshCompanyOrgModel() {
        logger.info("ENTER [/org/asyncRefreshCompanyOrgModel]");
//        logger.info("Request content[{}]", JSON.toJSONString(reqVo));
        long start = System.currentTimeMillis();
        GetCompanyOrgModelResVo resVo = new GetCompanyOrgModelResVo();
        try {
            // 1. param validation
//            if (result.hasErrors()) {
//                ObjectError objectError = result.getAllErrors().get(0);
//                logger.info(objectError.getDefaultMessage());
//                resVo.setMsg(ErrorEnum.RES_0002.getErrorMsg());
//                resVo.setState(ErrorEnum.RES_0002.getErrorCode());
//                return resVo;
//            }
            // 2. verify signature if needed
            // 3. business logic
            // session中获取用户信息
            SysUser currentUser = super.getCurrentUser();
            Integer userId = currentUser.getId();
            String userType = usersService.getUserTypeByUserId(currentUser.getId());
            if (userId == null) {
                logger.error(String.format("No login user bound with session id"));
                resVo.setState(ErrorEnum.RES_0002.getErrorCode());
                resVo.setMsg(ErrorEnum.RES_0002.getErrorMsg());
            } else {
                if (SpConstant.USER_TYPE.CORP_BUYER.equals(userType)) {
                    // find user info
                    SpCompanyBuyerUserModel buyerUserModel = (SpCompanyBuyerUserModel) usersService.findCommonUserModelByUserId(userId);
                    if (buyerUserModel != null) {
                        // 拿到OrgModel 不从redis拿 直接生成并推入redis
                        orgService.asyncRefreshRedisCompanyModelBySysCompanyId(buyerUserModel.getSysCompanyId(), buyerUserModel.getOrgType());
                        resVo.setState(ErrorEnum.RES_0000.getErrorCode());
                        resVo.setMsg(ErrorEnum.RES_0000.getErrorMsg());
                    } else {
                        logger.error(String.format("User info missing!"));
                        resVo.setState(ErrorEnum.RES_0002.getErrorCode());
                        resVo.setMsg(ErrorEnum.RES_0002.getErrorMsg() + " User info missing!");
                    }
                } else {
                    logger.error(String.format("User type %s is not supported!", userType));
                    resVo.setState(ErrorEnum.RES_0002.getErrorCode());
                    resVo.setMsg(ErrorEnum.RES_0002.getErrorMsg() + " Invalid user type!");
                }
            }
        } catch (RedisBusinessException e) {
            // error push model to redis, but no influence to business logic
            logger.error("[/org/asyncRefreshCompanyOrgModel]Request exception," + e.getMessage(), e);
            resVo.setState(ErrorEnum.RES_0000.getErrorCode());
            resVo.setMsg(ErrorEnum.RES_0000.getErrorMsg());
        } catch (BusinessException e) {
            logger.error("[/org/asyncRefreshCompanyOrgModel]Request exception," + e.getMessage(), e);
            resVo.setState(ErrorEnum.RES_0002.getErrorCode());
            resVo.setMsg(ErrorEnum.RES_0002.getErrorMsg() + e.getMessage());
        } catch (BeansException be) {
            logger.error("[/org/asyncRefreshCompanyOrgModel]Exception when copying beans," + be.getMessage(), be);
            resVo.setState(ErrorEnum.RES_0009.getErrorCode());
            resVo.setMsg(ErrorEnum.RES_0009.getErrorMsg() + be.getMessage());
        } catch (Exception e) {
            logger.error("[/org/asyncRefreshCompanyOrgModel]Unknown exception," + e.getMessage(), e);
            resVo.setState(ErrorEnum.RES_9999.getErrorCode());
            resVo.setMsg(ErrorEnum.RES_9999.getErrorMsg() + e.getMessage());

            MonitorModel monitorModel = new MonitorModel(ErrorEnum.RES_9999.getErrorCode(),
                    "/org/asyncRefreshCompanyOrgModel", JSON.toJSONString(null),
                    StringHelper.getNthLine(e.getMessage(), 10), JSON.toJSONString(super.getCurrentUser()), "Exception", SpConstant.MONITORING.PRIORITY.LEVEL2);
            Date date = new Date();
            jedisManager.setValueByKey(SpConstant.MONITORING.PREFIX.MONITOR_PREFIX +
                            String.valueOf(date.getTime()) +
                            "asyncRefreshCompanyOrgModel",
                    JSON.toJSONString(monitorModel), 86400);

        } finally {
            long costTime = System.currentTimeMillis() - start;
            logger.info("[/org/asyncRefreshCompanyOrgModel]Response content[{}], cost {} ms", JSON.toJSONString(resVo), costTime);
            return resVo;
        }
    }

    @CrossOrigin(origins = "*")
    @RequiresPermissions("sp:org:getCompanyOrgModel")
    @RequestMapping(value = "/getCompanyOrgModel", method = RequestMethod.GET)
    @ResponseBody
    public BaseRo getCompanyOrgModel() {
        logger.info("ENTER [/org/getCompanyOrgModel]");
//        logger.info("Request content[{}]", JSON.toJSONString(reqVo));
        long start = System.currentTimeMillis();
        GetCompanyOrgModelResVo resVo = new GetCompanyOrgModelResVo();
        try {
            // 1. param validation
//            if (result.hasErrors()) {
//                ObjectError objectError = result.getAllErrors().get(0);
//                logger.info(objectError.getDefaultMessage());
//                resVo.setMsg(ErrorEnum.RES_0002.getErrorMsg());
//                resVo.setState(ErrorEnum.RES_0002.getErrorCode());
//                return resVo;
//            }
            // 2. verify signature if needed
            // 3. business logic
            // session中获取用户信息
            SysUser currentUser = super.getCurrentUser();
            Integer userId = currentUser.getId();
            String userType = usersService.getUserTypeByUserId(currentUser.getId());
            if (userId == null) {
                logger.error(String.format("No login user bound with session id"));
                resVo.setState(ErrorEnum.RES_0002.getErrorCode());
                resVo.setMsg(ErrorEnum.RES_0002.getErrorMsg());
            } else {
                if (SpConstant.USER_TYPE.CORP_BUYER.equals(userType)) {
                    // find user info
                    SpCompanyBuyerUserModel buyerUserModel = (SpCompanyBuyerUserModel) usersService.findCommonUserModelByUserId(userId);
                    if (buyerUserModel != null) {
                        // 拿到OrgModel 优先从redis拿 redis拿不到再生成并推入redis
                        CompanyOrgBuyerModel buyerModel = (CompanyOrgBuyerModel) orgService.getCompanyModelFromRedisByUserId(userId, userType);
                        resVo.setOrgBuyerModel(buyerModel);
                        resVo.setState(ErrorEnum.RES_0000.getErrorCode());
                        resVo.setMsg(ErrorEnum.RES_0000.getErrorMsg());
                    } else {
                        logger.error(String.format("User info missing!"));
                        resVo.setState(ErrorEnum.RES_0002.getErrorCode());
                        resVo.setMsg(ErrorEnum.RES_0002.getErrorMsg() + " User info missing!");
                    }
                } else {
                    logger.error(String.format("User type %s is not supported!", userType));
                    resVo.setState(ErrorEnum.RES_0002.getErrorCode());
                    resVo.setMsg(ErrorEnum.RES_0002.getErrorMsg() + " Invalid user type!");
                }
            }
        } catch (RedisBusinessException e) {
            // error push model to redis, but no influence to business logic
            logger.error("[/org/getCompanyOrgModel]Request exception," + e.getMessage(), e);
            resVo.setState(ErrorEnum.RES_0000.getErrorCode());
            resVo.setMsg(ErrorEnum.RES_0000.getErrorMsg());
        } catch (BusinessException e) {
            logger.error("[/org/getCompanyOrgModel]Request exception," + e.getMessage(), e);
            resVo.setState(ErrorEnum.RES_0002.getErrorCode());
            resVo.setMsg(ErrorEnum.RES_0002.getErrorMsg() + e.getMessage());
        } catch (BeansException be) {
            logger.error("[/org/getCompanyOrgModel]Exception when copying beans," + be.getMessage(), be);
            resVo.setState(ErrorEnum.RES_0009.getErrorCode());
            resVo.setMsg(ErrorEnum.RES_0009.getErrorMsg() + be.getMessage());
        } catch (Exception e) {
            logger.error("[/org/getCompanyOrgModel]Unknown exception," + e.getMessage(), e);
            resVo.setState(ErrorEnum.RES_9999.getErrorCode());
            resVo.setMsg(ErrorEnum.RES_9999.getErrorMsg() + e.getMessage());

            MonitorModel monitorModel = new MonitorModel(ErrorEnum.RES_9999.getErrorCode(),
                    "/org/getCompanyOrgModel", JSON.toJSONString(null),
                    StringHelper.getNthLine(e.getMessage(), 10), JSON.toJSONString(super.getCurrentUser()), "Exception", SpConstant.MONITORING.PRIORITY.LEVEL2);
            Date date = new Date();
            jedisManager.setValueByKey(SpConstant.MONITORING.PREFIX.MONITOR_PREFIX +
                            String.valueOf(date.getTime()) +
                            "getCompanyOrgModel",
                    JSON.toJSONString(monitorModel), 86400);

        } finally {
            long costTime = System.currentTimeMillis() - start;
            logger.info("[/org/getCompanyOrgModel]Response content[{}], cost {} ms", JSON.toJSONString(resVo), costTime);
            return resVo;
        }
    }

    @CrossOrigin(origins = "*")
    @RequiresPermissions("sp:org:refreshVisibilityModel")
    @RequestMapping(value = "/refreshVisibilityModel", method = RequestMethod.GET)
    @ResponseBody
    public BaseRo refreshVisibilityModel() {
        logger.info("ENTER [/org/refreshVisibilityModel]");
//        logger.info("Request content[{}]", JSON.toJSONString(reqVo));
        long start = System.currentTimeMillis();
        GetVisibilityResVo resVo = new GetVisibilityResVo();
        try {
            // 1. param validation
//            if (result.hasErrors()) {
//                ObjectError objectError = result.getAllErrors().get(0);
//                logger.info(objectError.getDefaultMessage());
//                resVo.setMsg(ErrorEnum.RES_0002.getErrorMsg());
//                resVo.setState(ErrorEnum.RES_0002.getErrorCode());
//                return resVo;
//            }
            // 2. verify signature if needed
            // 3. business logic
            // session中获取用户信息
            SysUser currentUser = super.getCurrentUser();
            Integer userId = currentUser.getId();
            String userType = usersService.getUserTypeByUserId(currentUser.getId());
            if (userId == null) {
                logger.error(String.format("No login user bound with session id"));
                resVo.setState(ErrorEnum.RES_0002.getErrorCode());
                resVo.setMsg(ErrorEnum.RES_0002.getErrorMsg());
            } else {
                if (SpConstant.USER_TYPE.CORP_BUYER.equals(userType)) {
                    // find user info
                    SpCompanyBuyerUserModel buyerUserModel = (SpCompanyBuyerUserModel) usersService.findCommonUserModelByUserId(userId);
                    // 拿到OrgModel 优先从redis拿 redis拿不到再生成并推入redis
                    CompanyOrgBuyerModel buyerModel = (CompanyOrgBuyerModel) orgService.getCompanyModelFromRedisByUserId(userId, userType);
                    if (buyerUserModel == null || buyerModel == null) {
                        logger.error("Invalid company structure. Please contact SiRP for assistance.");
                        resVo.setState(ErrorEnum.RES_0002.getErrorCode());
                        resVo.setMsg(ErrorEnum.RES_0002.getErrorMsg() + " Key info missing!");
                    } else {
                        CompanyBuyerVisibilityModel buyerVisibilityModel = orgService.refreshRedisVisibilityModelForBuyer(userId, buyerUserModel.getRoleId(), buyerUserModel.getBuyerId(), buyerModel);
                        resVo.setBuyerVisibilityModel(buyerVisibilityModel);
                        resVo.setState(ErrorEnum.RES_0000.getErrorCode());
                        resVo.setMsg(ErrorEnum.RES_0000.getErrorMsg());
                    }
                } else {
                    logger.error(String.format("User type %s is not supported!", userType));
                    resVo.setState(ErrorEnum.RES_0002.getErrorCode());
                    resVo.setMsg(ErrorEnum.RES_0002.getErrorMsg() + " Invalid user type!");
                }
            }
        } catch (RedisBusinessException e) {
            // error push model to redis, but no influence to business logic
            logger.error("[/org/refreshVisibilityModel]Request exception," + e.getMessage(), e);
            resVo.setState(ErrorEnum.RES_0000.getErrorCode());
            resVo.setMsg(ErrorEnum.RES_0000.getErrorMsg());
        } catch (BusinessException e) {
            logger.error("[/org/refreshVisibilityModel]Request exception," + e.getMessage(), e);
            resVo.setState(ErrorEnum.RES_0002.getErrorCode());
            resVo.setMsg(ErrorEnum.RES_0002.getErrorMsg() + e.getMessage());
        } catch (BeansException be) {
            logger.error("[/org/refreshVisibilityModel]Exception when copying beans," + be.getMessage(), be);
            resVo.setState(ErrorEnum.RES_0009.getErrorCode());
            resVo.setMsg(ErrorEnum.RES_0009.getErrorMsg() + be.getMessage());
        } catch (Exception e) {
            logger.error("[/org/refreshVisibilityModel]Unknown exception," + e.getMessage(), e);
            resVo.setState(ErrorEnum.RES_9999.getErrorCode());
            resVo.setMsg(ErrorEnum.RES_9999.getErrorMsg() + e.getMessage());

            MonitorModel monitorModel = new MonitorModel(ErrorEnum.RES_9999.getErrorCode(),
                    "/org/refreshVisibilityModel", JSON.toJSONString(null),
                    StringHelper.getNthLine(e.getMessage(), 10), JSON.toJSONString(super.getCurrentUser()), "Exception", SpConstant.MONITORING.PRIORITY.LEVEL2);
            Date date = new Date();
            jedisManager.setValueByKey(SpConstant.MONITORING.PREFIX.MONITOR_PREFIX +
                            String.valueOf(date.getTime()) +
                            "refreshVisibilityModel",
                    JSON.toJSONString(monitorModel), 86400);

        } finally {
            long costTime = System.currentTimeMillis() - start;
            logger.info("[/org/refreshVisibilityModel]Response content[{}], cost {} ms", JSON.toJSONString(resVo), costTime);
            return resVo;
        }
    }

    @CrossOrigin(origins = "*")
    @RequiresPermissions("sp:org:asyncRefreshVisibilityModel")
    @RequestMapping(value = "/asyncRefreshVisibilityModel", method = RequestMethod.GET)
    @ResponseBody
    public BaseRo asyncRefreshVisibilityModel() {
        logger.info("ENTER [/org/asyncRefreshVisibilityModel]");
//        logger.info("Request content[{}]", JSON.toJSONString(reqVo));
        long start = System.currentTimeMillis();
        BaseRo resVo = new BaseRo();
        try {
            // 1. param validation
//            if (result.hasErrors()) {
//                ObjectError objectError = result.getAllErrors().get(0);
//                logger.info(objectError.getDefaultMessage());
//                resVo.setMsg(ErrorEnum.RES_0002.getErrorMsg());
//                resVo.setState(ErrorEnum.RES_0002.getErrorCode());
//                return resVo;
//            }
            // 2. verify signature if needed
            // 3. business logic
            // session中获取用户信息
            SysUser currentUser = super.getCurrentUser();
            Integer userId = currentUser.getId();
            String userType = usersService.getUserTypeByUserId(currentUser.getId());
            if (userId == null) {
                logger.error(String.format("No login user bound with session id"));
                resVo.setState(ErrorEnum.RES_0002.getErrorCode());
                resVo.setMsg(ErrorEnum.RES_0002.getErrorMsg());
            } else {
                if (SpConstant.USER_TYPE.CORP_BUYER.equals(userType)) {
                    // find user info
                    SpCompanyBuyerUserModel buyerUserModel = (SpCompanyBuyerUserModel) usersService.findCommonUserModelByUserId(userId);
                    // 拿到OrgModel 优先从redis拿 redis拿不到再生成并推入redis
                    CompanyOrgBuyerModel buyerModel = (CompanyOrgBuyerModel) orgService.getCompanyModelFromRedisByUserId(userId, userType);
                    if (buyerUserModel == null || buyerModel == null) {
                        logger.error("Invalid company structure. Please contact SiRP for assistance.");
                        resVo.setState(ErrorEnum.RES_0002.getErrorCode());
                        resVo.setMsg(ErrorEnum.RES_0002.getErrorMsg() + " Key info missing!");
                    } else {
                        orgService.asyncrefreshRedisVisibilityModelForBuyer(userId, buyerUserModel.getRoleId(), buyerUserModel.getBuyerId(), buyerModel);
                        resVo.setState(ErrorEnum.RES_0000.getErrorCode());
                        resVo.setMsg(ErrorEnum.RES_0000.getErrorMsg());
                    }
                } else {
                    logger.error(String.format("User type %s is not supported!", userType));
                    resVo.setState(ErrorEnum.RES_0002.getErrorCode());
                    resVo.setMsg(ErrorEnum.RES_0002.getErrorMsg() + " Invalid user type!");
                }
            }
        } catch (RedisBusinessException e) {
            // error push model to redis, but no influence to business logic
            logger.error("[/org/asyncRefreshVisibilityModel]Request exception," + e.getMessage(), e);
            resVo.setState(ErrorEnum.RES_0000.getErrorCode());
            resVo.setMsg(ErrorEnum.RES_0000.getErrorMsg());
        } catch (BusinessException e) {
            logger.error("[/org/asyncRefreshVisibilityModel]Request exception," + e.getMessage(), e);
            resVo.setState(ErrorEnum.RES_0002.getErrorCode());
            resVo.setMsg(ErrorEnum.RES_0002.getErrorMsg() + e.getMessage());
        } catch (BeansException be) {
            logger.error("[/org/asyncRefreshVisibilityModel]Exception when copying beans," + be.getMessage(), be);
            resVo.setState(ErrorEnum.RES_0009.getErrorCode());
            resVo.setMsg(ErrorEnum.RES_0009.getErrorMsg() + be.getMessage());
        } catch (Exception e) {
            logger.error("[/org/asyncRefreshVisibilityModel]Unknown exception," + e.getMessage(), e);
            resVo.setState(ErrorEnum.RES_9999.getErrorCode());
            resVo.setMsg(ErrorEnum.RES_9999.getErrorMsg() + e.getMessage());

            MonitorModel monitorModel = new MonitorModel(ErrorEnum.RES_9999.getErrorCode(),
                    "/org/asyncRefreshVisibilityModel", JSON.toJSONString(null),
                    StringHelper.getNthLine(e.getMessage(), 10), JSON.toJSONString(super.getCurrentUser()), "Exception", SpConstant.MONITORING.PRIORITY.LEVEL2);
            Date date = new Date();
            jedisManager.setValueByKey(SpConstant.MONITORING.PREFIX.MONITOR_PREFIX +
                            String.valueOf(date.getTime()) +
                            "asyncRefreshVisibilityModel",
                    JSON.toJSONString(monitorModel), 86400);

        } finally {
            long costTime = System.currentTimeMillis() - start;
            logger.info("[/org/asyncRefreshVisibilityModel]Response content[{}], cost {} ms", JSON.toJSONString(resVo), costTime);
            return resVo;
        }
    }

    @CrossOrigin(origins = "*")
    @RequiresPermissions("sp:org:getVisibilityModel")
    @RequestMapping(value = "/getVisibilityModel", method = RequestMethod.GET)
    @ResponseBody
    public BaseRo getVisibilityModel() {
        logger.info("ENTER [/org/getVisibilityModel]");
//        logger.info("Request content[{}]", JSON.toJSONString(reqVo));
        long start = System.currentTimeMillis();
        GetVisibilityResVo resVo = new GetVisibilityResVo();
        try {
            // 1. param validation
//            if (result.hasErrors()) {
//                ObjectError objectError = result.getAllErrors().get(0);
//                logger.info(objectError.getDefaultMessage());
//                resVo.setMsg(ErrorEnum.RES_0002.getErrorMsg());
//                resVo.setState(ErrorEnum.RES_0002.getErrorCode());
//                return resVo;
//            }
            // 2. verify signature if needed
            // 3. business logic
            // session中获取用户信息
            SysUser currentUser = super.getCurrentUser();
            Integer userId = currentUser.getId();
            String userType = usersService.getUserTypeByUserId(currentUser.getId());
            if (userId == null) {
                logger.error(String.format("No login user bound with session id"));
                resVo.setState(ErrorEnum.RES_0002.getErrorCode());
                resVo.setMsg(ErrorEnum.RES_0002.getErrorMsg());
            } else {
                if (SpConstant.USER_TYPE.CORP_BUYER.equals(userType)) {
                    // find user info
                    CompanyBuyerVisibilityModel buyerVisibilityModel = orgService.pullVisibilityModelFromRedisIfNoneGetNew(userId);
                    resVo.setBuyerVisibilityModel(buyerVisibilityModel);
                    resVo.setState(ErrorEnum.RES_0000.getErrorCode());
                    resVo.setMsg(ErrorEnum.RES_0000.getErrorMsg());
                } else {
                    logger.error(String.format("User type %s is not supported!", userType));
                    resVo.setState(ErrorEnum.RES_0002.getErrorCode());
                    resVo.setMsg(ErrorEnum.RES_0002.getErrorMsg() + " Invalid user type!");
                }
            }
        } catch (RedisBusinessException e) {
            // error push model to redis, but no influence to business logic
            logger.error("[/org/getVisibilityModel]Request exception," + e.getMessage(), e);
            resVo.setState(ErrorEnum.RES_0000.getErrorCode());
            resVo.setMsg(ErrorEnum.RES_0000.getErrorMsg());
        } catch (BusinessException e) {
            logger.error("[/org/getVisibilityModel]Request exception," + e.getMessage(), e);
            resVo.setState(ErrorEnum.RES_0002.getErrorCode());
            resVo.setMsg(ErrorEnum.RES_0002.getErrorMsg() + e.getMessage());
        } catch (BeansException be) {
            logger.error("[/org/getVisibilityModel]Exception when copying beans," + be.getMessage(), be);
            resVo.setState(ErrorEnum.RES_0009.getErrorCode());
            resVo.setMsg(ErrorEnum.RES_0009.getErrorMsg() + be.getMessage());
        } catch (Exception e) {
            logger.error("[/org/getVisibilityModel]Unknown exception," + e.getMessage(), e);
            resVo.setState(ErrorEnum.RES_9999.getErrorCode());
            resVo.setMsg(ErrorEnum.RES_9999.getErrorMsg() + e.getMessage());

            MonitorModel monitorModel = new MonitorModel(ErrorEnum.RES_9999.getErrorCode(),
                    "/org/getVisibilityModel", JSON.toJSONString(null),
                    StringHelper.getNthLine(e.getMessage(), 10), JSON.toJSONString(super.getCurrentUser()), "Exception", SpConstant.MONITORING.PRIORITY.LEVEL2);
            Date date = new Date();
            jedisManager.setValueByKey(SpConstant.MONITORING.PREFIX.MONITOR_PREFIX +
                            String.valueOf(date.getTime()) +
                            "getVisibilityModel",
                    JSON.toJSONString(monitorModel), 86400);

        } finally {
            long costTime = System.currentTimeMillis() - start;
            logger.info("[/org/getVisibilityModel]Response content[{}], cost {} ms", JSON.toJSONString(resVo), costTime);
            return resVo;
        }
    }

    @CrossOrigin(origins = "*")
    @RequiresPermissions("sp:org:getVisibleBuyerInfos")
    @RequestMapping(value = "/getVisibleBuyerInfos", method = RequestMethod.POST)
    @ResponseBody
    public BaseRo getVisibleBuyerInfos(@RequestBody @Valid GetVisibleBuyerInfosReqVo reqVo, BindingResult result) {
        logger.info("ENTER [/org/getVisibleBuyerInfos]");
        logger.info("Request content[{}]", JSON.toJSONString(reqVo));
        long start = System.currentTimeMillis();
        GetVisibleBuyerInfosResVo resVo = new GetVisibleBuyerInfosResVo();
        try {
//             1. param validation
            if (result.hasErrors()) {
                ObjectError objectError = result.getAllErrors().get(0);
                logger.info(objectError.getDefaultMessage());
                resVo.setMsg(ErrorEnum.RES_0002.getErrorMsg());
                resVo.setState(ErrorEnum.RES_0002.getErrorCode());
                return resVo;
            }
            // 2. verify signature if needed
            // 3. business logic
            // session中获取用户信息
            SysUser currentUser = super.getCurrentUser();
            Integer userId = currentUser.getId();
            String userType = usersService.getUserTypeByUserId(currentUser.getId());
            if (userId == null) {
                logger.error(String.format("No login user bound with session id"));
                resVo.setState(ErrorEnum.RES_0002.getErrorCode());
                resVo.setMsg(ErrorEnum.RES_0002.getErrorMsg());
            } else {
                if (SpConstant.USER_TYPE.CORP_BUYER.equals(userType)) {
                    SpCompanyBuyerUserModel buyerUserModel = (SpCompanyBuyerUserModel) usersService.findCommonUserModelByUserId(userId);
                    if ("0".equals(reqVo.getScenarioIndex())) {
                        List<VisibleBuyerInfosReturnData> buyerInfos = orgService.getVisibleBuyerInfosForBuyersIndex0(buyerUserModel.getBuyerId(), reqVo.getVisibleBuyerIds());
                        if (buyerInfos != null) {
                            resVo.setVisibleBuyerInfos(buyerInfos);
                            resVo.setState(ErrorEnum.RES_0000.getErrorCode());
                            resVo.setMsg(ErrorEnum.RES_0000.getErrorMsg());
                        }
                    }
                } else {
                    logger.error(String.format("User type %s is not supported!", userType));
                    resVo.setState(ErrorEnum.RES_0002.getErrorCode());
                    resVo.setMsg(ErrorEnum.RES_0002.getErrorMsg() + " Invalid user type!");
                }
            }
        } catch (BusinessException e) {
            logger.error("[/org/getVisibleBuyerInfos]Request exception," + e.getMessage(), e);
            resVo.setState(ErrorEnum.RES_0002.getErrorCode());
            resVo.setMsg(ErrorEnum.RES_0002.getErrorMsg() + e.getMessage());
        } catch (BeansException be) {
            logger.error("[/org/getVisibleBuyerInfos]Exception when copying beans," + be.getMessage(), be);
            resVo.setState(ErrorEnum.RES_0009.getErrorCode());
            resVo.setMsg(ErrorEnum.RES_0009.getErrorMsg() + be.getMessage());
        } catch (Exception e) {
            logger.error("[/org/getVisibleBuyerInfos]Unknown exception," + e.getMessage(), e);
            resVo.setState(ErrorEnum.RES_9999.getErrorCode());
            resVo.setMsg(ErrorEnum.RES_9999.getErrorMsg() + e.getMessage());


            MonitorModel monitorModel = new MonitorModel(ErrorEnum.RES_9999.getErrorCode(),
                    "/org/getVisibleBuyerInfos", JSON.toJSONString(reqVo),
                    StringHelper.getNthLine(e.getMessage(), 10), JSON.toJSONString(super.getCurrentUser()), "Exception", SpConstant.MONITORING.PRIORITY.LEVEL2);
            Date date = new Date();
            jedisManager.setValueByKey(SpConstant.MONITORING.PREFIX.MONITOR_PREFIX +
                            String.valueOf(date.getTime()) +
                            "getVisibleBuyerInfos",
                    JSON.toJSONString(monitorModel), 86400);


        } finally {
            long costTime = System.currentTimeMillis() - start;
            logger.info("[/org/getVisibleBuyerInfos]Response content[{}], cost {} ms", JSON.toJSONString(resVo), costTime);
            return resVo;
        }
    }

    @CrossOrigin(origins = "*")
    @RequiresPermissions("sp:org:inviteNewCompanyBuyer")
    @RequestMapping(value = "/inviteNewCompanyBuyer", method = RequestMethod.POST)
    @ResponseBody
    public BaseRo inviteNewCompanyBuyer(@RequestBody @Valid InviteCompanyBuyerReqVo reqVo, BindingResult result) {
        logger.info("ENTER [/org/inviteNewCompanyBuyer]");
        logger.info("Request content[{}]", JSON.toJSONString(reqVo));
        long start = System.currentTimeMillis();
        BaseRo resVo = new BaseRo();
        try {
//             1. param validation
            if (result.hasErrors()) {
                ObjectError objectError = result.getAllErrors().get(0);
                logger.info(objectError.getDefaultMessage());
                resVo.setMsg(ErrorEnum.RES_0002.getErrorMsg());
                resVo.setState(ErrorEnum.RES_0002.getErrorCode());
                return resVo;
            }
            // session中获取用户信息
            SysUser currentUser = super.getCurrentUser();
            Integer userId = currentUser.getId();
            String userType = usersService.getUserTypeByUserId(currentUser.getId());
            // 3. business logic
            if (userId == null) {
                logger.error(String.format("No login user bound with session id"));
                resVo.setState(ErrorEnum.RES_0002.getErrorCode());
                resVo.setMsg(ErrorEnum.RES_0002.getErrorMsg());
            } else {
//                // 2. 检查该公司用户的关键信息编辑权限
//                SpSysCompanySuperuserRef superuserRef = companySuperuserRefDao.selectByPrimaryKey(userId);
//                if (superuserRef == null) {
//                    throw new BusinessException(String.format("Permission denied!"));
//                }
                if (SpConstant.USER_TYPE.CORP_BUYER.equals(userType)) {
                    orgService.inviteNewCompanyBuyer(userId, reqVo);
                    resVo.setState(ErrorEnum.RES_0000.getErrorCode());
                    resVo.setMsg(ErrorEnum.RES_0000.getErrorMsg());
                } else {
                    logger.error(String.format("User type %s is not supported!", userType));
                    resVo.setState(ErrorEnum.RES_0002.getErrorCode());
                    resVo.setMsg(ErrorEnum.RES_0002.getErrorMsg() + " Invalid user type!");
                }
            }
        } catch (BusinessException e) {
            logger.error("[/org/inviteNewCompanyBuyer]Request exception," + e.getMessage(), e);
            resVo.setState(ErrorEnum.RES_0002.getErrorCode());
            resVo.setMsg(ErrorEnum.RES_0002.getErrorMsg() + e.getMessage());
        } catch (BeansException be) {
            logger.error("[/org/inviteNewCompanyBuyer]Exception when copying beans," + be.getMessage(), be);
            resVo.setState(ErrorEnum.RES_0009.getErrorCode());
            resVo.setMsg(ErrorEnum.RES_0009.getErrorMsg() + be.getMessage());
        } catch (Exception e) {
            logger.error("[/org/inviteNewCompanyBuyer]Unknown exception," + e.getMessage(), e);
            resVo.setState(ErrorEnum.RES_9999.getErrorCode());
            resVo.setMsg(ErrorEnum.RES_9999.getErrorMsg() + e.getMessage());

            MonitorModel monitorModel = new MonitorModel(ErrorEnum.RES_9999.getErrorCode(),
                    "/org/inviteNewCompanyBuyer", JSON.toJSONString(reqVo),
                    StringHelper.getNthLine(e.getMessage(), 10), JSON.toJSONString(super.getCurrentUser()), "Exception", SpConstant.MONITORING.PRIORITY.LEVEL1);
            Date date = new Date();
            jedisManager.setValueByKey(SpConstant.MONITORING.PREFIX.MONITOR_PREFIX +
                            String.valueOf(date.getTime()) +
                            "inviteNewCompanyBuyer",
                    JSON.toJSONString(monitorModel), 86400);


        } finally {
            long costTime = System.currentTimeMillis() - start;
            logger.info("[/org/inviteNewCompanyBuyer]Response content[{}], cost {} ms", JSON.toJSONString(resVo), costTime);
            return resVo;
        }
    }

    @CrossOrigin(origins = "*")
    @RequiresPermissions("sp:org:undoInviteNewCompanyBuyer")
    @RequestMapping(value = "/undoInviteNewCompanyBuyer", method = RequestMethod.POST)
    @ResponseBody
    public BaseRo undoInviteNewCompanyBuyer(@RequestBody @Valid UndoInviteCompanyBuyerReqVo reqVo, BindingResult result) {
        logger.info("ENTER [/org/undoInviteNewCompanyBuyer]");
        logger.info("Request content[{}]", JSON.toJSONString(reqVo));
        long start = System.currentTimeMillis();
        BaseRo resVo = new BaseRo();
        try {
//             1. param validation
            if (result.hasErrors()) {
                ObjectError objectError = result.getAllErrors().get(0);
                logger.info(objectError.getDefaultMessage());
                resVo.setMsg(ErrorEnum.RES_0002.getErrorMsg());
                resVo.setState(ErrorEnum.RES_0002.getErrorCode());
                return resVo;
            }
            // session中获取用户信息
            SysUser currentUser = super.getCurrentUser();
            Integer userId = currentUser.getId();
            String userType = usersService.getUserTypeByUserId(currentUser.getId());
            // 3. business logic
            if (userId == null) {
                logger.error(String.format("No login user bound with session id"));
                resVo.setState(ErrorEnum.RES_0002.getErrorCode());
                resVo.setMsg(ErrorEnum.RES_0002.getErrorMsg());
            } else {
//                // 2. 检查该公司用户的关键信息编辑权限
//                SpSysCompanySuperuserRef superuserRef = companySuperuserRefDao.selectByPrimaryKey(userId);
//                if (superuserRef == null) {
//                    throw new BusinessException(String.format("Permission denied!"));
//                }
                if (SpConstant.USER_TYPE.CORP_BUYER.equals(userType)) {
                    orgService.undoInviteNewCompanyBuyer(userId, reqVo);
                    resVo.setState(ErrorEnum.RES_0000.getErrorCode());
                    resVo.setMsg(ErrorEnum.RES_0000.getErrorMsg());
                } else {
                    logger.error(String.format("User type %s is not supported!", userType));
                    resVo.setState(ErrorEnum.RES_0002.getErrorCode());
                    resVo.setMsg(ErrorEnum.RES_0002.getErrorMsg() + " Invalid user type!");
                }
            }
        } catch (BusinessException e) {
            logger.error("[/org/undoInviteNewCompanyBuyer]Request exception," + e.getMessage(), e);
            resVo.setState(ErrorEnum.RES_0002.getErrorCode());
            resVo.setMsg(ErrorEnum.RES_0002.getErrorMsg() + e.getMessage());
        } catch (BeansException be) {
            logger.error("[/org/undoInviteNewCompanyBuyer]Exception when copying beans," + be.getMessage(), be);
            resVo.setState(ErrorEnum.RES_0009.getErrorCode());
            resVo.setMsg(ErrorEnum.RES_0009.getErrorMsg() + be.getMessage());
        } catch (Exception e) {
            logger.error("[/org/undoInviteNewCompanyBuyer]Unknown exception," + e.getMessage(), e);
            resVo.setState(ErrorEnum.RES_9999.getErrorCode());
            resVo.setMsg(ErrorEnum.RES_9999.getErrorMsg() + e.getMessage());

            MonitorModel monitorModel = new MonitorModel(ErrorEnum.RES_9999.getErrorCode(),
                    "/org/undoInviteNewCompanyBuyer", JSON.toJSONString(reqVo),
                    StringHelper.getNthLine(e.getMessage(), 10), JSON.toJSONString(super.getCurrentUser()), "Exception", SpConstant.MONITORING.PRIORITY.LEVEL1);
            Date date = new Date();
            jedisManager.setValueByKey(SpConstant.MONITORING.PREFIX.MONITOR_PREFIX +
                            String.valueOf(date.getTime()) +
                            "undoInviteNewCompanyBuyer",
                    JSON.toJSONString(monitorModel), 86400);

        } finally {
            long costTime = System.currentTimeMillis() - start;
            logger.info("[/org/undoInviteNewCompanyBuyer]Response content[{}], cost {} ms", JSON.toJSONString(resVo), costTime);
            return resVo;
        }
    }

    @CrossOrigin(origins = "*")
    @RequiresPermissions("sp:org:addNewBuyerOrgNode")
    @RequestMapping(value = "/addNewBuyerOrgNode", method = RequestMethod.POST)
    @ResponseBody
    public BaseRo addNewBuyerOrgNode(@RequestBody @Valid AddNewBuyerOrgNodeReqVo reqVo, BindingResult result) {
        logger.info("ENTER [/org/addNewBuyerOrgNode]");
        logger.info("Request content[{}]", JSON.toJSONString(reqVo));
        long start = System.currentTimeMillis();
        BaseRo resVo = new BaseRo();
        try {
//             1. param validation
            if (result.hasErrors()) {
                ObjectError objectError = result.getAllErrors().get(0);
                logger.info(objectError.getDefaultMessage());
                resVo.setMsg(ErrorEnum.RES_0002.getErrorMsg());
                resVo.setState(ErrorEnum.RES_0002.getErrorCode());
                return resVo;
            }
            // session中获取用户信息
            SysUser currentUser = super.getCurrentUser();
            Integer userId = currentUser.getId();
            String userType = usersService.getUserTypeByUserId(currentUser.getId());
            // 3. business logic
            if (userId == null) {
                logger.error(String.format("No login user bound with session id"));
                resVo.setState(ErrorEnum.RES_0002.getErrorCode());
                resVo.setMsg(ErrorEnum.RES_0002.getErrorMsg());
            } else {
//                // 2. 检查该公司用户的关键信息编辑权限
//                SpSysCompanySuperuserRef superuserRef = companySuperuserRefDao.selectByPrimaryKey(userId);
//                if (superuserRef == null) {
//                    throw new BusinessException(String.format("Permission denied!"));
//                }
                if (SpConstant.USER_TYPE.CORP_BUYER.equals(userType)) {
                    orgService.addNewBuyerOrgNode(userId, reqVo);
                    resVo.setState(ErrorEnum.RES_0000.getErrorCode());
                    resVo.setMsg(ErrorEnum.RES_0000.getErrorMsg());
                } else {
                    logger.error(String.format("User type %s is not supported!", userType));
                    resVo.setState(ErrorEnum.RES_0002.getErrorCode());
                    resVo.setMsg(ErrorEnum.RES_0002.getErrorMsg() + " Invalid user type!");
                }
            }
        } catch (BusinessException e) {
            logger.error("[/org/addNewBuyerOrgNode]Request exception," + e.getMessage(), e);
            resVo.setState(ErrorEnum.RES_0002.getErrorCode());
            resVo.setMsg(ErrorEnum.RES_0002.getErrorMsg() + e.getMessage());
        } catch (BeansException be) {
            logger.error("[/org/addNewBuyerOrgNode]Exception when copying beans," + be.getMessage(), be);
            resVo.setState(ErrorEnum.RES_0009.getErrorCode());
            resVo.setMsg(ErrorEnum.RES_0009.getErrorMsg() + be.getMessage());
        } catch (Exception e) {
            logger.error("[/org/addNewBuyerOrgNode]Unknown exception," + e.getMessage(), e);
            resVo.setState(ErrorEnum.RES_9999.getErrorCode());
            resVo.setMsg(ErrorEnum.RES_9999.getErrorMsg() + e.getMessage());

            MonitorModel monitorModel = new MonitorModel(ErrorEnum.RES_9999.getErrorCode(),
                    "/org/addNewBuyerOrgNode", JSON.toJSONString(reqVo),
                    StringHelper.getNthLine(e.getMessage(), 10), JSON.toJSONString(super.getCurrentUser()), "Exception", SpConstant.MONITORING.PRIORITY.LEVEL1);
            Date date = new Date();
            jedisManager.setValueByKey(SpConstant.MONITORING.PREFIX.MONITOR_PREFIX +
                            String.valueOf(date.getTime()) +
                            "addNewBuyerOrgNode",
                    JSON.toJSONString(monitorModel), 86400);

        } finally {
            long costTime = System.currentTimeMillis() - start;
            logger.info("[/org/addNewBuyerOrgNode]Response content[{}], cost {} ms", JSON.toJSONString(resVo), costTime);
            return resVo;
        }
    }

    @CrossOrigin(origins = "*")
    @RequiresPermissions("sp:org:editBuyerOrgNodeInfo")
    @RequestMapping(value = "/editBuyerOrgNodeInfo", method = RequestMethod.POST)
    @ResponseBody
    public BaseRo editBuyerOrgNodeInfo(@RequestBody @Valid EditBuyerOrgNodeReqVo reqVo, BindingResult result) {
        logger.info("ENTER [/org/editBuyerOrgNodeInfo]");
        logger.info("Request content[{}]", JSON.toJSONString(reqVo));
        long start = System.currentTimeMillis();
        BaseRo resVo = new BaseRo();
        try {
//             1. param validation
            if (result.hasErrors()) {
                ObjectError objectError = result.getAllErrors().get(0);
                logger.info(objectError.getDefaultMessage());
                resVo.setMsg(ErrorEnum.RES_0002.getErrorMsg());
                resVo.setState(ErrorEnum.RES_0002.getErrorCode());
                return resVo;
            }
            // session中获取用户信息
            SysUser currentUser = super.getCurrentUser();
            Integer userId = currentUser.getId();
            String userType = usersService.getUserTypeByUserId(currentUser.getId());
            // 3. business logic
            if (userId == null) {
                logger.error(String.format("No login user bound with session id"));
                resVo.setState(ErrorEnum.RES_0002.getErrorCode());
                resVo.setMsg(ErrorEnum.RES_0002.getErrorMsg());
            } else {
//                // 2. 检查该公司用户的关键信息编辑权限
//                SpSysCompanySuperuserRef superuserRef = companySuperuserRefDao.selectByPrimaryKey(userId);
//                if (superuserRef == null) {
//                    throw new BusinessException(String.format("Permission denied!"));
//                }
                if (SpConstant.USER_TYPE.CORP_BUYER.equals(userType)) {
                    orgService.editBuyerOrgNodeInfo(userId, reqVo);
                    resVo.setState(ErrorEnum.RES_0000.getErrorCode());
                    resVo.setMsg(ErrorEnum.RES_0000.getErrorMsg());
                } else {
                    logger.error(String.format("User type %s is not supported!", userType));
                    resVo.setState(ErrorEnum.RES_0002.getErrorCode());
                    resVo.setMsg(ErrorEnum.RES_0002.getErrorMsg() + " Invalid user type!");
                }
            }
        } catch (BusinessException e) {
            logger.error("[/org/editBuyerOrgNodeInfo]Request exception," + e.getMessage(), e);
            resVo.setState(ErrorEnum.RES_0002.getErrorCode());
            resVo.setMsg(ErrorEnum.RES_0002.getErrorMsg() + e.getMessage());
        } catch (BeansException be) {
            logger.error("[/org/editBuyerOrgNodeInfo]Exception when copying beans," + be.getMessage(), be);
            resVo.setState(ErrorEnum.RES_0009.getErrorCode());
            resVo.setMsg(ErrorEnum.RES_0009.getErrorMsg() + be.getMessage());
        } catch (Exception e) {
            logger.error("[/org/editBuyerOrgNodeInfo]Unknown exception," + e.getMessage(), e);
            resVo.setState(ErrorEnum.RES_9999.getErrorCode());
            resVo.setMsg(ErrorEnum.RES_9999.getErrorMsg() + e.getMessage());

            MonitorModel monitorModel = new MonitorModel(ErrorEnum.RES_9999.getErrorCode(),
                    "/org/editBuyerOrgNodeInfo", JSON.toJSONString(reqVo),
                    StringHelper.getNthLine(e.getMessage(), 10), JSON.toJSONString(super.getCurrentUser()), "Exception", SpConstant.MONITORING.PRIORITY.LEVEL1);
            Date date = new Date();
            jedisManager.setValueByKey(SpConstant.MONITORING.PREFIX.MONITOR_PREFIX +
                            String.valueOf(date.getTime()) +
                            "editBuyerOrgNodeInfo",
                    JSON.toJSONString(monitorModel), 86400);

        } finally {
            long costTime = System.currentTimeMillis() - start;
            logger.info("[/org/editBuyerOrgNodeInfo]Response content[{}], cost {} ms", JSON.toJSONString(resVo), costTime);
            return resVo;
        }
    }

    @CrossOrigin(origins = "*")
    @RequiresPermissions("sp:org:editBuyerOrgNodeMounts")
    @RequestMapping(value = "/editBuyerOrgNodeMounts", method = RequestMethod.POST)
    @ResponseBody
    public BaseRo editBuyerOrgNodeMounts(@RequestBody @Valid EditOrgNodeMountBuyerReqVo reqVo, BindingResult result) {
        logger.info("ENTER [/org/editBuyerOrgNodeMounts]");
        logger.info("Request content[{}]", JSON.toJSONString(reqVo));
        long start = System.currentTimeMillis();
        BaseRo resVo = new BaseRo();
        try {
//             1. param validation
            if (result.hasErrors()) {
                ObjectError objectError = result.getAllErrors().get(0);
                logger.info(objectError.getDefaultMessage());
                resVo.setMsg(ErrorEnum.RES_0002.getErrorMsg());
                resVo.setState(ErrorEnum.RES_0002.getErrorCode());
                return resVo;
            }
            // session中获取用户信息
            SysUser currentUser = super.getCurrentUser();
            Integer userId = currentUser.getId();
            String userType = usersService.getUserTypeByUserId(currentUser.getId());
            // 3. business logic
            if (userId == null) {
                logger.error(String.format("No login user bound with session id"));
                resVo.setState(ErrorEnum.RES_0002.getErrorCode());
                resVo.setMsg(ErrorEnum.RES_0002.getErrorMsg());
            } else {
//                // 2. 检查该公司用户的关键信息编辑权限
//                SpSysCompanySuperuserRef superuserRef = companySuperuserRefDao.selectByPrimaryKey(userId);
//                if (superuserRef == null) {
//                    throw new BusinessException(String.format("Permission denied!"));
//                }
                if (SpConstant.USER_TYPE.CORP_BUYER.equals(userType)) {
                    orgService.editBuyerOrgNodeMounts(userId, reqVo);
                    resVo.setState(ErrorEnum.RES_0000.getErrorCode());
                    resVo.setMsg(ErrorEnum.RES_0000.getErrorMsg());
                } else {
                    logger.error(String.format("User type %s is not supported!", userType));
                    resVo.setState(ErrorEnum.RES_0002.getErrorCode());
                    resVo.setMsg(ErrorEnum.RES_0002.getErrorMsg() + " Invalid user type!");
                }
            }
        } catch (BusinessException e) {
            logger.error("[/org/editBuyerOrgNodeMounts]Request exception," + e.getMessage(), e);
            resVo.setState(ErrorEnum.RES_0002.getErrorCode());
            resVo.setMsg(ErrorEnum.RES_0002.getErrorMsg() + e.getMessage());
        } catch (BeansException be) {
            logger.error("[/org/editBuyerOrgNodeMounts]Exception when copying beans," + be.getMessage(), be);
            resVo.setState(ErrorEnum.RES_0009.getErrorCode());
            resVo.setMsg(ErrorEnum.RES_0009.getErrorMsg() + be.getMessage());
        } catch (Exception e) {
            logger.error("[/org/editBuyerOrgNodeMounts]Unknown exception," + e.getMessage(), e);
            resVo.setState(ErrorEnum.RES_9999.getErrorCode());
            resVo.setMsg(ErrorEnum.RES_9999.getErrorMsg() + e.getMessage());

            MonitorModel monitorModel = new MonitorModel(ErrorEnum.RES_9999.getErrorCode(),
                    "/org/editBuyerOrgNodeMounts", JSON.toJSONString(reqVo),
                    StringHelper.getNthLine(e.getMessage(), 10), JSON.toJSONString(super.getCurrentUser()), "Exception", SpConstant.MONITORING.PRIORITY.LEVEL1);
            Date date = new Date();
            jedisManager.setValueByKey(SpConstant.MONITORING.PREFIX.MONITOR_PREFIX +
                            String.valueOf(date.getTime()) +
                            "editBuyerOrgNodeMounts",
                    JSON.toJSONString(monitorModel), 86400);


        } finally {
            long costTime = System.currentTimeMillis() - start;
            logger.info("[/org/editBuyerOrgNodeMounts]Response content[{}], cost {} ms", JSON.toJSONString(resVo), costTime);
            return resVo;
        }
    }

    @CrossOrigin(origins = "*")
    @RequestMapping(value = "/test01", method = RequestMethod.GET)
    @ResponseBody
    public BaseRo test01() {
        logger.info("ENTER [/org/test01]");
//        logger.info("Request content[{}]", JSON.toJSONString(reqVo));
        long start = System.currentTimeMillis();
        BaseRo resVo = new BaseRo();
        try {
            // 1. param validation
//            if (result.hasErrors()) {
//                ObjectError objectError = result.getAllErrors().get(0);
//                logger.info(objectError.getDefaultMessage());
//                resVo.setMsg(ErrorEnum.RES_0002.getErrorMsg());
//                resVo.setState(ErrorEnum.RES_0002.getErrorCode());
//                return resVo;
//            }
            // 2. verify signature if needed
            // 3. business logic
            // session中获取用户信息
//            SysUser currentUser = super.getCurrentUser();
//            String userType = usersService.getUserTypeByUserId(currentUser.getId());
            Integer userId = 20;
            String userType = "03";

            if (userId == null) {
                logger.error(String.format("No login user bound with session id"));
                resVo.setState(ErrorEnum.RES_0002.getErrorCode());
                resVo.setMsg(ErrorEnum.RES_0002.getErrorMsg());
            } else {
                if (SpConstant.USER_TYPE.BUYER.equals(userType)) {
                    // find user info
                    SpBuyerUserModel userModel = (SpBuyerUserModel) usersService.findCommonUserModelByUserId(userId);
                    // find user customization
                    resVo.setState(ErrorEnum.RES_0000.getErrorCode());
                    resVo.setMsg(ErrorEnum.RES_0000.getErrorMsg());
                } else if (SpConstant.USER_TYPE.CORP_BUYER.equals(userType)) {
                    // find user info
                    CompanyOrgBuyerModel companyOrgBuyerModel = (CompanyOrgBuyerModel) orgService.findCompanyModelByUserId(userId, userType);
                    logger.info(String.format("companyOrgBuyerModel: [%s]", JSON.toJSONString(companyOrgBuyerModel)));
                    orgService.pushCompanyModel2Redis(companyOrgBuyerModel);
                    CompanyOrgBuyerModel companyOrgBuyerModel2 = (CompanyOrgBuyerModel) orgService.getCompanyModelFromRedis(companyOrgBuyerModel.getSysCompanyId(), companyOrgBuyerModel.getOrgType());
                    logger.info(String.format("companyOrgBuyerModel2: [%s]", JSON.toJSONString(companyOrgBuyerModel2)));
//                    userModel = usersService.findCommonUserModelByUserId(userId);
                    // find user customization
                    CompanyBuyerVisibilityModel visibilityModel = orgService.getVisibilityModelForBuyer(userId, "131", "BYR20190312143147606", companyOrgBuyerModel);
                    resVo.setState(ErrorEnum.RES_0000.getErrorCode());
                    resVo.setMsg(ErrorEnum.RES_0000.getErrorMsg());
                } else {
                    logger.error(String.format("User type %s is not supportable!", userType));
                    resVo.setState(ErrorEnum.RES_0002.getErrorCode());
                    resVo.setMsg(ErrorEnum.RES_0002.getErrorMsg() + " Invalid user type");
                }
            }
        } catch (BusinessException e) {
            logger.error("[/org/test01]Request exception," + e.getMessage(), e);
            resVo.setState(ErrorEnum.RES_0002.getErrorCode());
            resVo.setMsg(ErrorEnum.RES_0002.getErrorMsg() + e.getMessage());
        } catch (BeansException be) {
            logger.error("[/org/test01]Exception when copying beans," + be.getMessage(), be);
            resVo.setState(ErrorEnum.RES_0009.getErrorCode());
            resVo.setMsg(ErrorEnum.RES_0009.getErrorMsg() + be.getMessage());
        } catch (Exception e) {
            logger.error("[/org/test01]Unknown exception," + e.getMessage(), e);
            resVo.setState(ErrorEnum.RES_9999.getErrorCode());
            resVo.setMsg(ErrorEnum.RES_9999.getErrorMsg() + e.getMessage());

            MonitorModel monitorModel = new MonitorModel(ErrorEnum.RES_9999.getErrorCode(),
                    "/org/test01", JSON.toJSONString(null),
                    StringHelper.getNthLine(e.getMessage(), 10), JSON.toJSONString(super.getCurrentUser()), "Exception", SpConstant.MONITORING.PRIORITY.LEVEL3);
            Date date = new Date();
            jedisManager.setValueByKey(SpConstant.MONITORING.PREFIX.MONITOR_PREFIX +
                            String.valueOf(date.getTime()) +
                            "test01",
                    JSON.toJSONString(monitorModel), 86400);


        } finally {
            long costTime = System.currentTimeMillis() - start;
            logger.info("[/org/test01]Response content[{}], cost {} ms", JSON.toJSONString(resVo), costTime);
            return resVo;
        }
    }


    @CrossOrigin(origins = "*")
    @RequiresPermissions("sp:org:getBuyerAssignee")
    @RequestMapping(value = "/getBuyerAssignee", method = RequestMethod.GET)
    @ResponseBody
    public BaseRo getBuyerAssignee() {
        logger.info("ENTER [ORG GET BUYER ASSIGNEE INFO]");
//        logger.info("Request content[{}]", JSON.toJSONString(reqVo));
        long start = System.currentTimeMillis();
        GetBuyerAssigneeResVo resVo = new GetBuyerAssigneeResVo();

        try {
            // 1. param validation
//            if (result.hasErrors()) {
//                ObjectError objectError = result.getAllErrors().get(0);
//                logger.info(objectError.getDefaultMessage());
//                resVo.setMsg(ErrorEnum.RES_0002.getErrorMsg() + objectError.getDefaultMessage());
//                resVo.setState(ErrorEnum.RES_0002.getErrorCode());
//                return resVo;
//            }

            // 2. verify signature if needed
            SysUser currentUser = super.getCurrentUser();
            String userType = usersService.getUserTypeByUserId(currentUser.getId());

            if (SpConstant.USER_TYPE.BUYER.equals(userType)) {
                // 3. business logic
                // 如果待查询数据与
                resVo.setState(ErrorEnum.RES_0013.getErrorCode());
                resVo.setMsg(ErrorEnum.RES_0013.getErrorMsg());
            } else if (SpConstant.USER_TYPE.CORP_BUYER.equals(userType)) {
                // 3. business logic
                // 如果待查询数据与
                CompanyBuyerVisibilityModel buyerVisibilityModel = orgService.pullVisibilityModelFromRedisIfNoneGetNew(currentUser.getId());
                List<VisibleBuyerInfosReturnData> returnData = orgService.getVisibleBuyerInfosForBuyersIndex0(buyerVisibilityModel.getBuyerId(), new ArrayList<>(buyerVisibilityModel.getAllBuyerIds()));
                resVo.setData(returnData);
                resVo.setState(ErrorEnum.RES_0000.getErrorCode());
                resVo.setMsg(ErrorEnum.RES_0000.getErrorMsg());
            } else {
                // 预留其他userType的处理逻辑
                resVo.setState(ErrorEnum.RES_0013.getErrorCode());
                resVo.setMsg(ErrorEnum.RES_0013.getErrorMsg());
            }
        } catch (BusinessException e) {
            logger.error("[/org/getBuyerAssignee] Request exception: " + e.getMessage(), e);
            resVo.setMsg(ErrorEnum.RES_0002.getErrorMsg() + e.getMessage());
            resVo.setState(ErrorEnum.RES_0002.getErrorCode());
        } catch (Exception e) {
            logger.error("[/org/getBuyerAssignee] Request exception: " + e.getMessage(), e);
            resVo.setMsg(ErrorEnum.RES_9999.getErrorMsg() + e.getMessage());
            resVo.setState(ErrorEnum.RES_9999.getErrorCode());


            MonitorModel monitorModel = new MonitorModel(ErrorEnum.RES_9999.getErrorCode(),
                    "/org/getBuyerAssignee", JSON.toJSONString(null),
                    StringHelper.getNthLine(e.getMessage(), 10), JSON.toJSONString(super.getCurrentUser()), "Exception", SpConstant.MONITORING.PRIORITY.LEVEL3);
            Date date = new Date();
            jedisManager.setValueByKey(SpConstant.MONITORING.PREFIX.MONITOR_PREFIX +
                            String.valueOf(date.getTime()) +
                            "getBuyerAssignee",
                    JSON.toJSONString(monitorModel), 86400);


        } finally {
            long costTime = System.currentTimeMillis() - start;
            logger.info("Response content[{}], cost {}ms", JSON.toJSONString(resVo), costTime);
            return resVo;
        }
    }

}
