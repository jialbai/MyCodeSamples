package com.sirp.platform.sirpapi.org.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.sirp.platform.base.mail.SpMailUtil;
import com.sirp.platform.common.constant.SpConstant;
import com.sirp.platform.common.exception.BusinessException;
import com.sirp.platform.common.exception.RedisBusinessException;
import com.sirp.platform.common.utils.DateUtil;
import com.sirp.platform.common.utils.PropertiesUtil;
import com.sirp.platform.core.redis.JedisManager;
import com.sirp.platform.sirpapi.base.companymodel.org.*;
import com.sirp.platform.sirpapi.base.companymodel.visibility.CompanyBuyerVisibilityModel;
import com.sirp.platform.sirpapi.domain.*;
import com.sirp.platform.sirpapi.domain.extend.PartAssignedToAnotherBuyerObjExtension;
import com.sirp.platform.sirpapi.domain.finder.SpOrgUserRefExtFinder;
import com.sirp.platform.sirpapi.domain.model.SpOrgUserRefExtSysCompanyRetModel;
import com.sirp.platform.sirpapi.org.bo.PendingBuyerInvitationObj;
import com.sirp.platform.sirpapi.org.service.OrgService;
import com.sirp.platform.sirpapi.org.vo.*;
import com.sirp.platform.sirpapi.org.vo.returndata.VisibleBuyerInfosReturnData;
import com.sirp.platform.sirpapi.persistence.*;
import com.sirp.platform.sirpapi.persistence.extend.SpOrgUserRefExtMapper;
import com.sirp.platform.sirpapi.users.service.UsersService;
import com.sirp.platform.sirpapi.users.vo.model.SpCompanyBuyerUserModel;
import com.sirp.platform.system.domain.User;
import com.sirp.platform.system.domain.UserExample;
import com.sirp.platform.system.domain.UserRoleRef;
import com.sirp.platform.system.domain.UserRoleRefExample;
import com.sirp.platform.system.persistence.UserMapper;
import com.sirp.platform.system.persistence.UserRoleRefMapper;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service("orgService")
@EnableAsync
public class OrgServiceImpl implements OrgService {
    private static final Logger logger = Logger.getLogger(OrgServiceImpl.class);
    @Autowired
    private UsersService usersService;
    @Autowired
    private SpOrgUserRefExtMapper orgUserRefExtDao;
    @Autowired
    private SpOrgUserRefMapper orgUserRefDao;
    @Autowired
    private SpOrgInfoMapper orgInfoDao;
    @Autowired
    private UserMapper userDao;
    @Autowired
    private SpBuyerInfoMapper buyerInfoDao;
    @Autowired
    private SpPartsAssignMapper partsAssignDao;
    @Autowired
    private SpCustomizedSupplierAssignMapper supplierAssignDao;
    @Autowired
    private SpSysCompanyInfoMapper sysCompanyInfoDao;
    @Autowired
    private SpSysCompanyMembershipMapper sysCompanyMembershipDao;
    @Autowired
    private SpSysCompanyPendingInvitationMapper pendingInvitationDao;
    @Autowired
    private SpSysCompanySuperuserRefMapper superuserRefDao;
    @Autowired
    private SpMailUtil spMailUtil;
    @Autowired
    private SpUserActionRecMapper spUserActionRecDao;
    @Autowired
    private UserRoleRefMapper userRoleRefDao;
    @Autowired
    private JedisManager jedisManager;

//    @Override
    public CompanyOrgCommonModel getCompanyModelBySysCompanyId(String sysCompanyId, String entityType) throws Exception{
        // 每个org的entityType只能是03或者04 在org这个level不能为03+04
        switch(entityType){
            case SpConstant.COMPANY_TYPE.SYS_BUYER_COMPANY:
            {
                CompanyOrgBuyerModel buyerModel = new CompanyOrgBuyerModel();
                buyerModel.setOrgType(entityType);
                buyerModel.setSysCompanyId(sysCompanyId);
                SpOrgInfoExample example = new SpOrgInfoExample();
                example.createCriteria().andSysCompanyIdEqualTo(sysCompanyId).andOrgTypeEqualTo(entityType);//.andDelStatusEqualTo("0");// 未删除
                example.setOrderByClause("org_level,create_time asc");
                List<SpOrgInfo> orgCodeList = orgInfoDao.selectByExample(example);
                if (orgCodeList != null && orgCodeList.size() > 0) {
                    List<BuyerOrgNode> orgNodeList = new ArrayList<>();
                    for (SpOrgInfo orgInfo : orgCodeList) {
                        BuyerOrgNode buyerOrgNode = new BuyerOrgNode();
                        buyerOrgNode.setDelStatus(orgInfo.getDelStatus());
                        buyerOrgNode.setOrgCode(orgInfo.getOrgCode());
                        buyerOrgNode.setOrgLevel(orgInfo.getOrgLevel());
                        buyerOrgNode.setOrgStatus(orgInfo.getOrgStatus());
                        buyerOrgNode.setOrgName(orgInfo.getOrgName());
                        buyerOrgNode.setParentOrgCode(orgInfo.getParentOrgCode());
                        buyerOrgNode.setMountAdminBuyerIds(getMountAdminBuyerIdsByOrgCode(orgInfo.getOrgCode()));
                        buyerOrgNode.setUnmountAdminBuyerIds(getUnmountAdminBuyerIdsByOrgCode(orgInfo.getOrgCode()));
                        buyerOrgNode.setMountUserBuyerIds(getMountUserBuyerIdsByOrgCode(orgInfo.getOrgCode()));
                        buyerOrgNode.setUnmountUserBuyerIds(getUnmountUserBuyerIdsByOrgCode(orgInfo.getOrgCode()));
                        buyerOrgNode.setPendingBuyerInvitations(getPendingBuyerByOrgCode(orgInfo.getOrgCode()));
                        orgNodeList.add(buyerOrgNode);
                    }
                    buyerModel.setBuyerOrgNodes(orgNodeList);
                    return buyerModel;
                } else {
                    return null;
                }
            }
            case SpConstant.COMPANY_TYPE.SYS_SUPPLIER_COMPANY:
                // TODO
            {
                CompanyOrgSupplierModel supplierModel = new CompanyOrgSupplierModel();
                supplierModel.setOrgType(entityType);
                supplierModel.setSysCompanyId(sysCompanyId);
                SpOrgInfoExample example = new SpOrgInfoExample();
                example.createCriteria().andSysCompanyIdEqualTo(sysCompanyId).andOrgTypeEqualTo(entityType);//.andDelStatusEqualTo("0");// 未删除
                example.setOrderByClause("org_level,create_time asc");
                List<SpOrgInfo> orgCodeList = orgInfoDao.selectByExample(example);
                if (orgCodeList != null && orgCodeList.size() > 0) {
                    List<SysSupplierOrgNode> orgNodeList = new ArrayList<>();
                    for (SpOrgInfo orgInfo : orgCodeList) {
                        SysSupplierOrgNode sysSupplierOrgNode = new SysSupplierOrgNode();
                        sysSupplierOrgNode.setDelStatus(orgInfo.getDelStatus());
                        sysSupplierOrgNode.setOrgCode(orgInfo.getOrgCode());
                        sysSupplierOrgNode.setOrgLevel(orgInfo.getOrgLevel());
                        sysSupplierOrgNode.setOrgStatus(orgInfo.getOrgStatus());
                        sysSupplierOrgNode.setOrgName(orgInfo.getOrgName());
                        sysSupplierOrgNode.setParentOrgCode(orgInfo.getParentOrgCode());
                        // TODO 完善下面4行代码
//                        sysSupplierOrgNode.setMountAdminSysSupplierIds(getMountAdminSysSupplierIdsByOrgCode(orgInfo.getOrgCode()));
//                        sysSupplierOrgNode.setUnmountAdminSysSupplierIds(getUnmountAdminSysSupplierIdsByOrgCode(orgInfo.getOrgCode()));
//                        sysSupplierOrgNode.setMountUserSysSupplierIds(getMountUserSysSupplierIdsByOrgCode(orgInfo.getOrgCode()));
//                        sysSupplierOrgNode.setUnmountUserSysSupplierIds(getUnmountUserSysSupplierIdsByOrgCode(orgInfo.getOrgCode()));
                        orgNodeList.add(sysSupplierOrgNode);
                    }
                    supplierModel.setSysSupplierOrgNodes(orgNodeList);
                    return supplierModel;
                } else {
                    return null;
                }
            }
            default: // 默认buyer_company
            {
                CompanyOrgBuyerModel buyerModel = new CompanyOrgBuyerModel();
                buyerModel.setOrgType(entityType);
                buyerModel.setSysCompanyId(sysCompanyId);
                SpOrgInfoExample example = new SpOrgInfoExample();
                example.createCriteria().andSysCompanyIdEqualTo(sysCompanyId).andOrgTypeEqualTo(entityType);//.andDelStatusEqualTo("0");// 未删除
                example.setOrderByClause("org_level,create_time asc");
                List<SpOrgInfo> orgCodeList = orgInfoDao.selectByExample(example);
                if (orgCodeList != null && orgCodeList.size() > 0) {
                    List<BuyerOrgNode> orgNodeList = new ArrayList<>();
                    for (SpOrgInfo orgInfo : orgCodeList) {
                        BuyerOrgNode buyerOrgNode = new BuyerOrgNode();
                        buyerOrgNode.setDelStatus(orgInfo.getDelStatus());
                        buyerOrgNode.setOrgCode(orgInfo.getOrgCode());
                        buyerOrgNode.setOrgLevel(orgInfo.getOrgLevel());
                        buyerOrgNode.setOrgStatus(orgInfo.getOrgStatus());
                        buyerOrgNode.setOrgName(orgInfo.getOrgName());
                        buyerOrgNode.setParentOrgCode(orgInfo.getParentOrgCode());
                        buyerOrgNode.setMountAdminBuyerIds(getMountAdminBuyerIdsByOrgCode(orgInfo.getOrgCode()));
                        buyerOrgNode.setUnmountAdminBuyerIds(getUnmountAdminBuyerIdsByOrgCode(orgInfo.getOrgCode()));
                        buyerOrgNode.setMountUserBuyerIds(getMountUserBuyerIdsByOrgCode(orgInfo.getOrgCode()));
                        buyerOrgNode.setUnmountUserBuyerIds(getUnmountUserBuyerIdsByOrgCode(orgInfo.getOrgCode()));
                        buyerOrgNode.setPendingBuyerInvitations(getPendingBuyerByOrgCode(orgInfo.getOrgCode()));
                        orgNodeList.add(buyerOrgNode);
                    }
                    buyerModel.setBuyerOrgNodes(orgNodeList);
                    return buyerModel;
                } else {
                    return null;
                }
            }
        }
    }

    private Set<PendingBuyerInvitationObj> getPendingBuyerByOrgCode(String orgCode) {
        Set<PendingBuyerInvitationObj> retSet = new HashSet<>();
        SpSysCompanyPendingInvitationExample example = new SpSysCompanyPendingInvitationExample();
        example.createCriteria().andOrgCodeEqualTo(orgCode);
        List<SpSysCompanyPendingInvitation> invitations = pendingInvitationDao.selectByExample(example);
        if (invitations != null && invitations.size() > 0) {
            for (SpSysCompanyPendingInvitation invitation : invitations) {
                PendingBuyerInvitationObj obj = new PendingBuyerInvitationObj();
                BeanUtils.copyProperties(invitation, obj);
                User user = userDao.selectByPrimaryKey(invitation.getInviterUserId());
                obj.setInviterEmail(user.getEmail());
                retSet.add(obj);
            }
        }
        return retSet;
    }

    @Override
    public CompanyOrgCommonModel refreshRedisCompanyModelBySysCompanyId(String sysCompanyId, String entityType) throws Exception{
        // Step1: 拿到model
        CompanyOrgCommonModel model = getCompanyModelBySysCompanyId(sysCompanyId, entityType);
        // Step2: 推到redis
        if (model != null) {
            pushCompanyModel2Redis(model);
        }
        return model;
    }

    @Override
    @Async
    public void asyncRefreshRedisCompanyModelBySysCompanyId(String sysCompanyId, String entityType) throws Exception{
        // Step1: 拿到model
        CompanyOrgCommonModel model = getCompanyModelBySysCompanyId(sysCompanyId, entityType);
        // Step2: 推到redis
        if (model != null) {
            pushCompanyModel2Redis(model);
        }
    }

    @Override
    public CompanyBuyerVisibilityModel refreshRedisVisibilityModelForBuyer(Integer userId, String roleId, String buyerId, CompanyOrgBuyerModel buyerModel) throws Exception{
        // Step1: 拿到model
        CompanyBuyerVisibilityModel model = getVisibilityModelForBuyer(userId, roleId, buyerId, buyerModel);
        // Step2: 推到redis
        if (model != null) {
            pushCompanyBuyerVisibilityModelToRedis(model);
        }
        return model;
    }

    @Override
    @Async
    public void asyncrefreshRedisVisibilityModelForBuyer(Integer userId, String roleId, String buyerId, CompanyOrgBuyerModel buyerModel) throws Exception{
        // Step1: 拿到model
        CompanyBuyerVisibilityModel model = getVisibilityModelForBuyer(userId, roleId, buyerId, buyerModel);
        // Step2: 推到redis
        if (model != null) {
            pushCompanyBuyerVisibilityModelToRedis(model);
        }
    }




    @Override
    public void pushCompanyModel2Redis(CompanyOrgCommonModel companyOrgCommonModel) throws Exception{
        // Step2: 推到redis
        Date now = new Date();
        long nowInLong = now.getTime();
        if (companyOrgCommonModel instanceof CompanyOrgBuyerModel) {
            CompanyOrgBuyerModel buyerModel = (CompanyOrgBuyerModel) companyOrgCommonModel;
            String redisKey = SpConstant.ORG.REDIS_KEY_PREFIX_COMPANY + buyerModel.getSysCompanyId() + buyerModel.getOrgType();
            jedisManager.setValueByKey(redisKey, JSONObject.toJSONString(companyOrgCommonModel), SpConstant.REDIS.COMPANY_KEEP_HOURS * 3600);
        } else if (companyOrgCommonModel instanceof CompanyOrgSupplierModel) {
            CompanyOrgSupplierModel supplierModel = (CompanyOrgSupplierModel) companyOrgCommonModel;
            String redisKey = SpConstant.ORG.REDIS_KEY_PREFIX_COMPANY + supplierModel.getSysCompanyId() + supplierModel.getOrgType();
            jedisManager.setValueByKey(redisKey, JSONObject.toJSONString(companyOrgCommonModel), SpConstant.REDIS.COMPANY_KEEP_HOURS * 3600);
        } else if (companyOrgCommonModel instanceof CompanyOrgBuyerSupplierModel) {
            CompanyOrgBuyerSupplierModel buyerSupplierModel = (CompanyOrgBuyerSupplierModel) companyOrgCommonModel;
            String redisKeyBuyer = SpConstant.ORG.REDIS_KEY_PREFIX_COMPANY + buyerSupplierModel.getBuyerModel().getSysCompanyId() + buyerSupplierModel.getBuyerModel().getOrgType();
            String redisKeySupplier = SpConstant.ORG.REDIS_KEY_PREFIX_COMPANY + buyerSupplierModel.getSysSupplierModel().getSysCompanyId() + buyerSupplierModel.getSysSupplierModel().getOrgType();
            // 还原单独的buyer/supplier model
            CompanyOrgBuyerModel buyerModel = buyerSupplierModel.getBuyerModel();
            CompanyOrgSupplierModel supplierModel = buyerSupplierModel.getSysSupplierModel();
            jedisManager.setValueByKey(redisKeyBuyer, JSONObject.toJSONString(buyerModel), SpConstant.REDIS.COMPANY_KEEP_HOURS * 3600);
            jedisManager.setValueByKey(redisKeySupplier, JSONObject.toJSONString(supplierModel), SpConstant.REDIS.COMPANY_KEEP_HOURS * 3600);
        }
    }

    @Override
    public CompanyOrgCommonModel getCompanyModelFromRedis(String sysCompanyId, String entityType) throws Exception{
        if (StringUtils.isNotBlank(sysCompanyId)) {
            String redisKey = SpConstant.ORG.REDIS_KEY_PREFIX_COMPANY + sysCompanyId + entityType;
            String companyModelInJson = jedisManager.getValueBykey(redisKey);
            if (StringUtils.isNotBlank(companyModelInJson)) {
                switch(entityType) {
                    case SpConstant.SYS_COMPANY.TYPE.BUYER:
                        return JSONObject.parseObject(companyModelInJson,CompanyOrgBuyerModel.class);
                    case SpConstant.SYS_COMPANY.TYPE.SUPPLIER:
                        return JSONObject.parseObject(companyModelInJson,CompanyOrgSupplierModel.class);
                    default:
                        return JSONObject.parseObject(companyModelInJson,CompanyOrgBuyerModel.class);
                }
            } else {
                // redisKey已经失效 执行refresh
                return refreshRedisCompanyModelBySysCompanyId(sysCompanyId, entityType);
            }
        } else {
            return null;
        }
    }

    @Override
    public CompanyOrgCommonModel getCompanyModelFromRedisByUserId(Integer userId, String userType) throws Exception{
        switch(userType) {
            case SpConstant.USER_TYPE.CORP_BUYER:
            {
                SpOrgUserRef ref =  orgUserRefDao.selectByPrimaryKey(userId, SpConstant.ORG.TYPE.BUYER);
                if (ref != null) {
                    return getCompanyModelFromRedis(ref.getSysCompanyId(), ref.getMappingType());
                }
            }
            case SpConstant.USER_TYPE.CORP_SUPPLIER:
            {
                SpOrgUserRef ref =  orgUserRefDao.selectByPrimaryKey(userId, SpConstant.ORG.TYPE.SUPPLIER);
                if (ref != null) {
                    return getCompanyModelFromRedis(ref.getSysCompanyId(), ref.getMappingType());
                }
            }
            case SpConstant.USER_TYPE.CORP_BUYER_SUPPLIER:
            {
                CompanyOrgBuyerSupplierModel buyerSupplierModel = new CompanyOrgBuyerSupplierModel();
                CompanyOrgBuyerModel buyerModel = new CompanyOrgBuyerModel();
                CompanyOrgSupplierModel supplierModel = new CompanyOrgSupplierModel();
                // buyer org
                SpOrgUserRef ref =  orgUserRefDao.selectByPrimaryKey(userId, SpConstant.ORG.TYPE.BUYER);
                if (ref != null) {
                    buyerModel = (CompanyOrgBuyerModel) getCompanyModelFromRedis(ref.getSysCompanyId(), ref.getMappingType());
                    buyerSupplierModel.setBuyerModel(buyerModel);
                }
                // supplier org
                SpOrgUserRef refSupplier =  orgUserRefDao.selectByPrimaryKey(userId, SpConstant.ORG.TYPE.SUPPLIER);
                if (refSupplier != null) {
                    supplierModel = (CompanyOrgSupplierModel) getCompanyModelFromRedis(refSupplier.getSysCompanyId(), refSupplier.getMappingType());
                    buyerSupplierModel.setSysSupplierModel(supplierModel);
                }
                return buyerSupplierModel;
            }
            default:
                return null;
        }
    }

    @Override
    public CompanyOrgCommonModel findCompanyModelByUserId(Integer userId, String userType) throws Exception{
        switch(userType) {
            case SpConstant.USER_TYPE.CORP_BUYER:
                return findCompanyOrgBuyerModelByUserId(userId);
            case SpConstant.USER_TYPE.CORP_SUPPLIER:
                return findCompanyOrgSupplierModelByUserId(userId);
            case SpConstant.USER_TYPE.CORP_BUYER_SUPPLIER:
                return findCompanyOrgBuyerSupplierModelByUserId(userId);
            default:
                return null;
        }
    }

    private CompanyOrgCommonModel findCompanyOrgBuyerModelByUserId(Integer userId) throws Exception {
        // 检查该user是否处于挂载状态
        SpOrgUserRef orgUserRef = orgUserRefDao.selectByPrimaryKey(userId, SpConstant.ORG.TYPE.BUYER);
        if (orgUserRef == null || SpConstant.ORG.MOUNT_STATUS.NO.equals(orgUserRef.getMountStatus())) {
            logger.info(String.format("User[%d] isn't in any company or it's now unmount to the company!", userId));
            return null;
        }
        // 拿到sysCompanyId和entityType
        SpOrgUserRefExtSysCompanyRetModel sysCompanyRetModel = orgUserRefExtDao.getSysCompanyIdAndTypeByUserId(userId);
        return getCompanyModelBySysCompanyId(sysCompanyRetModel.getSysCompanyId(),sysCompanyRetModel.getEntityType());
    }
    private CompanyOrgCommonModel findCompanyOrgSupplierModelByUserId(Integer userId) throws Exception{
        // 检查该user是否处于挂载状态
        SpOrgUserRef orgUserRef = orgUserRefDao.selectByPrimaryKey(userId, SpConstant.ORG.TYPE.SUPPLIER);
        if (orgUserRef == null || SpConstant.ORG.MOUNT_STATUS.NO.equals(orgUserRef.getMountStatus())) {
            logger.info(String.format("User[%d] isn't in any company or it's currently unmount to the company!", userId));
            return null;
        }
        // 拿到sysCompanyId和entityType
        SpOrgUserRefExtSysCompanyRetModel sysCompanyRetModel = orgUserRefExtDao.getSysCompanyIdAndTypeByUserId(userId);
        return getCompanyModelBySysCompanyId(sysCompanyRetModel.getSysCompanyId(),sysCompanyRetModel.getEntityType());
    }
    private CompanyOrgCommonModel findCompanyOrgBuyerSupplierModelByUserId(Integer userId) throws Exception{
        CompanyOrgBuyerSupplierModel model = new CompanyOrgBuyerSupplierModel();
        SpOrgUserRef buyerRef = orgUserRefDao.selectByPrimaryKey(userId, SpConstant.ORG.TYPE.BUYER);
        if (buyerRef != null) {
            CompanyOrgBuyerModel buyerModel = new CompanyOrgBuyerModel();
            buyerModel.setSysCompanyId(buyerRef.getSysCompanyId());
            buyerModel.setOrgType(buyerRef.getMappingType());
            buyerModel.setBuyerOrgNodes(((CompanyOrgBuyerModel) findCompanyOrgBuyerModelByUserId(userId)).getBuyerOrgNodes());
            model.setBuyerModel(buyerModel);
        }
        SpOrgUserRef supplierRef = orgUserRefDao.selectByPrimaryKey(userId, SpConstant.ORG.TYPE.SUPPLIER);
        if (supplierRef != null) {
            CompanyOrgSupplierModel supplierModel = new CompanyOrgSupplierModel();
            supplierModel.setSysCompanyId(supplierRef.getSysCompanyId());
            supplierModel.setOrgType(supplierRef.getMappingType());
            supplierModel.setSysSupplierOrgNodes(((CompanyOrgSupplierModel) findCompanyOrgBuyerModelByUserId(userId)).getSysSupplierOrgNodes());
            model.setSysSupplierModel(supplierModel);
        }
        return model;
    }

    @Override
    public CompanyBuyerVisibilityModel getVisibilityModelForBuyer(Integer userId, String roleId, String buyerId, CompanyOrgBuyerModel buyerModel) throws Exception {
        CompanyBuyerVisibilityModel retBuyerModel = new CompanyBuyerVisibilityModel();
        retBuyerModel.setBuyerId(buyerId);
        Set<String> visibleBuyerIds = new HashSet<>();
        Set<String> allBuyerIds = new HashSet<>();
        // assigned in
        Set<String> fcInSuppliers = new HashSet<>();
        Set<String> rwInSuppliers = new HashSet<>();
        Set<String> roInSuppliers = new HashSet<>();
        Set<String> fcInSysSuppliers = new HashSet<>();
        Set<String> rwInSysSuppliers = new HashSet<>();
        Set<String> roInSysSuppliers = new HashSet<>();
        Set<String> fcInParts = new HashSet<>();
        Set<String> rwInParts = new HashSet<>();
        Set<String> roInParts = new HashSet<>();
        // assigned out
        Set<String> fcOutSuppliers = new HashSet<>();
        Set<String> rwOutSuppliers = new HashSet<>();
        Set<String> roOutSuppliers = new HashSet<>();
        Set<String> fcOutSysSuppliers = new HashSet<>();
        Set<String> rwOutSysSuppliers = new HashSet<>();
        Set<String> roOutSysSuppliers = new HashSet<>();
        Set<String> fcOutParts = new HashSet<>();
        Set<String> rwOutParts = new HashSet<>();
        Set<String> roOutParts = new HashSet<>();
        // 完全不可见其他buyer的情况
        List<String> adminRoleIds = new ArrayList<>();
        adminRoleIds.add(SpConstant.ROLE_GROUP.CORP_ORG_ADMIN.BUYER.VISITOR);
        adminRoleIds.add(SpConstant.ROLE_GROUP.CORP_ORG_ADMIN.BUYER.TRIAL);
        adminRoleIds.add(SpConstant.ROLE_GROUP.CORP_ORG_ADMIN.BUYER.EXPIRED);
        adminRoleIds.add(SpConstant.ROLE_GROUP.CORP_ORG_ADMIN.BUYER.ACTIVE);
        // 1. roleId不属于Admin的buyer
        if (!adminRoleIds.contains(roleId)) {
//            visibleBuyerIds.add(buyerId);
            retBuyerModel.setVisibleBuyerIds(visibleBuyerIds);
        } else {
            // 2. roleId属于Admin, 但 a.没有处于某个公司架构中 或者 b.没有挂载在某个orgCode上 的buyer
            SpOrgUserRef orgUserRef = orgUserRefDao.selectByPrimaryKey(userId, SpConstant.ORG.TYPE.BUYER);
            if (orgUserRef == null || SpConstant.ORG.MOUNT_STATUS.NO.equals(orgUserRef.getMountStatus())) {
//                visibleBuyerIds.add(buyerId);
                retBuyerModel.setVisibleBuyerIds(visibleBuyerIds);
            } else {
                // 属于Admin，过滤出可见的node
                BuyerOrgNode thisOrgNode = locateThisBuyerInCompanyModel(userId, roleId, buyerId, buyerModel);
                // 如果无法找出node 或者 该node的del_status处于'被删除'状态
                if (thisOrgNode == null || SpConstant.ORG.DEL_STATUS.YES.equals(thisOrgNode.getDelStatus())) {
//                    visibleBuyerIds.add(buyerId);
                    retBuyerModel.setVisibleBuyerIds(visibleBuyerIds);
                } else {
//                    visibleBuyerIds.add(buyerId);
                    // 过滤所有visible的buyerId
                    filterVisibleOrgCodes(thisOrgNode, buyerModel, visibleBuyerIds);
                    visibleBuyerIds.remove(buyerId);
                    retBuyerModel.setVisibleBuyerIds(visibleBuyerIds);
                }
            }
        }
        // assigned-in suppliers
        SpCustomizedSupplierAssignExample example = new SpCustomizedSupplierAssignExample();
        example.createCriteria().andAssigneeBuyerIdEqualTo(buyerId);
        List<SpCustomizedSupplierAssign> assignList = supplierAssignDao.selectByExample(example);
        if (assignList != null && assignList.size() > 0) {
            for (SpCustomizedSupplierAssign assign : assignList) {
                if (SpConstant.SUPPLIER_ASSIGN.PERMISSION.FULL_CONTROL.equals(assign.getAssigneePermission())) {
                    fcInSuppliers.add(assign.getSupplierId());
                } else if (SpConstant.SUPPLIER_ASSIGN.PERMISSION.READ_WRITE.equals(assign.getAssigneePermission())) {
                    rwInSuppliers.add(assign.getSupplierId());
                } else if (SpConstant.SUPPLIER_ASSIGN.PERMISSION.READ_ONLY.equals(assign.getAssigneePermission())) {
                    roInSuppliers.add(assign.getSupplierId());
                }
            }
        }
        retBuyerModel.setFcInSuppliers(fcInSuppliers);
        retBuyerModel.setRoInSuppliers(roInSuppliers);
        retBuyerModel.setRwInSuppliers(rwInSuppliers);
        // parts assigned-in to this buyer
        Set<PartAssignedToAnotherBuyerObjExtension> assignedParts = new HashSet<>();
        SpPartsAssignExample example4 = new SpPartsAssignExample();
        example4.createCriteria().andAssigneeBuyerIdEqualTo(buyerId).andAssignTypeEqualTo(SpConstant.PART_ASSIGN_TYPE.BUYER_2_BUYER);
        List<SpPartsAssign> partsAssigns = partsAssignDao.selectByExample(example4);
        if (partsAssigns != null && partsAssigns.size() > 0) {
            for (SpPartsAssign assign : partsAssigns) {
                PartAssignedToAnotherBuyerObjExtension tempObj = new PartAssignedToAnotherBuyerObjExtension();
                BeanUtils.copyProperties(assign, tempObj);
                assignedParts.add(tempObj);
                if (SpConstant.SUPPLIER_ASSIGN.PERMISSION.FULL_CONTROL.equals(assign.getAssigneePermission())) {
                    fcInParts.add(assign.getPartId());
                } else if (SpConstant.SUPPLIER_ASSIGN.PERMISSION.READ_WRITE.equals(assign.getAssigneePermission())) {
                    rwInParts.add(assign.getPartId());
                } else if (SpConstant.SUPPLIER_ASSIGN.PERMISSION.READ_ONLY.equals(assign.getAssigneePermission())) {
                    roInParts.add(assign.getPartId());
                }
            }
        }
        retBuyerModel.setFcInParts(fcInParts);
        retBuyerModel.setRwInParts(rwInParts);
        retBuyerModel.setRoInParts(roInParts);
        retBuyerModel.setAssignedInParts(assignedParts);
        // assigned-out suppliers
        SpCustomizedSupplierAssignExample example2 = new SpCustomizedSupplierAssignExample();
        example2.createCriteria().andAssignerBuyerIdEqualTo(buyerId);
        List<SpCustomizedSupplierAssign> assignList2 = supplierAssignDao.selectByExample(example2);
        if (assignList2 != null && assignList2.size() > 0) {
            for (SpCustomizedSupplierAssign assign : assignList2) {
                if (SpConstant.SUPPLIER_ASSIGN.PERMISSION.FULL_CONTROL.equals(assign.getAssigneePermission())) {
                    fcOutSuppliers.add(assign.getSupplierId());
                } else if (SpConstant.SUPPLIER_ASSIGN.PERMISSION.READ_WRITE.equals(assign.getAssigneePermission())) {
                    rwOutSuppliers.add(assign.getSupplierId());
                } else if (SpConstant.SUPPLIER_ASSIGN.PERMISSION.READ_ONLY.equals(assign.getAssigneePermission())) {
                    roOutSuppliers.add(assign.getSupplierId());
                }
            }
        }
        retBuyerModel.setFcOutSuppliers(fcOutSuppliers);
        retBuyerModel.setRoOutSuppliers(roOutSuppliers);
        retBuyerModel.setRwOutSuppliers(rwOutSuppliers);
        // parts assigned-out to other buyer
        Set<PartAssignedToAnotherBuyerObjExtension> assignedPartsOut = new HashSet<>();
        SpPartsAssignExample example5 = new SpPartsAssignExample();
        example5.createCriteria().andAssignerBuyerIdEqualTo(buyerId).andAssignTypeEqualTo(SpConstant.PART_ASSIGN_TYPE.BUYER_2_BUYER);
        List<SpPartsAssign> partsAssigns5 = partsAssignDao.selectByExample(example5);
        if (partsAssigns5 != null && partsAssigns5.size() > 0) {
            for (SpPartsAssign assign : partsAssigns5) {
                PartAssignedToAnotherBuyerObjExtension tempObj = new PartAssignedToAnotherBuyerObjExtension();
                BeanUtils.copyProperties(assign, tempObj);
                assignedPartsOut.add(tempObj);
                if (SpConstant.SUPPLIER_ASSIGN.PERMISSION.FULL_CONTROL.equals(assign.getAssigneePermission())) {
                    fcOutParts.add(assign.getPartId());
                } else if (SpConstant.SUPPLIER_ASSIGN.PERMISSION.READ_WRITE.equals(assign.getAssigneePermission())) {
                    rwOutParts.add(assign.getPartId());
                } else if (SpConstant.SUPPLIER_ASSIGN.PERMISSION.READ_ONLY.equals(assign.getAssigneePermission())) {
                    roOutParts.add(assign.getPartId());
                }
            }
        }
        retBuyerModel.setFcOutParts(fcOutParts);
        retBuyerModel.setRwOutParts(rwOutParts);
        retBuyerModel.setRoOutParts(roOutParts);
        retBuyerModel.setAssignedOutParts(assignedPartsOut);
        // allBuyerIds
        allBuyerIds.add(buyerId);
        allBuyerIds.addAll(visibleBuyerIds);
        retBuyerModel.setAllBuyerIds(allBuyerIds);
        // TODO sysSupplier logics
        return retBuyerModel;
    }

    @Override
    public List<VisibleBuyerInfosReturnData> getVisibleBuyerInfosForBuyersIndex0(String thisBuyer, List<String> visibleBuyerIds) throws Exception {
        List<VisibleBuyerInfosReturnData> retList = new ArrayList<>();
        List<String> corpAdminRoleIds = new ArrayList<>();
        corpAdminRoleIds.add(SpConstant.ROLE_GROUP.CORP_ORG_ADMIN.BUYER.ACTIVE);
        corpAdminRoleIds.add(SpConstant.ROLE_GROUP.CORP_ORG_ADMIN.BUYER.TRIAL);
        corpAdminRoleIds.add(SpConstant.ROLE_GROUP.CORP_ORG_ADMIN.BUYER.EXPIRED);
        corpAdminRoleIds.add(SpConstant.ROLE_GROUP.CORP_ORG_ADMIN.BUYER.VISITOR);
        for (String buyerId : visibleBuyerIds) {
            VisibleBuyerInfosReturnData data = new VisibleBuyerInfosReturnData();
            SpBuyerInfoExample buyerInfoExample = new SpBuyerInfoExample();
            buyerInfoExample.createCriteria().andBuyerIdEqualTo(buyerId).andSysCompanyIdIsNotNull();
            List<SpBuyerInfo> buyerInfoList = buyerInfoDao.selectByExample(buyerInfoExample);
            if (buyerInfoList != null && buyerInfoList.size() == 1) {
                SpBuyerInfo sbi = buyerInfoList.get(0);
                Integer userId = sbi.getUserId();
                SpSysCompanySuperuserRef superuserRef = superuserRefDao.selectByPrimaryKey(userId);
                if (superuserRef != null) {
                    data.setIsSuperAdmin(SpConstant.SYS_COMPANY.IS_SUPER_ADMIN.YES);
                } else {
                    data.setIsSuperAdmin(SpConstant.SYS_COMPANY.IS_SUPER_ADMIN.NO);
                }
                User sysUser = userDao.selectByPrimaryKey(userId);
                if (SpConstant.USER_TYPE.CORP_BUYER.equals(sysUser.getUserType())) {
                    SpOrgUserRef userRef = orgUserRefDao.selectByPrimaryKey(userId, SpConstant.ORG.TYPE.BUYER);
                    data.setBuyerId(sbi.getBuyerId());
                    data.setBuyerAlias(sbi.getLastname() + ", " + sbi.getFirstname() + " - " + sysUser.getEmail());
                    if (buyerId.equals(thisBuyer)) {
                        data.setBuyerDesc("Myself");
                    }
                    data.setOrgInfo(userRef.getOrgName());
                    data.setMountStatus(userRef.getMountStatus());
                    data.setIsAdmin(corpAdminRoleIds.contains(sbi.getRoleId())? "1" : "0");
                    data.setEmail(sbi.getUsername());
                    data.setOrgCode(userRef.getOrgCode());
                }
            } else {
                data.setBuyerAlias("Unknown");
                data.setBuyerId(buyerId);
                data.setBuyerDesc("");
                data.setOrgInfo("Unknown");
            }
            retList.add(data);
        }
        return retList;
    }

    @Override
    public void pushCompanyBuyerVisibilityModelToRedis(CompanyBuyerVisibilityModel buyerVisibilityModel) throws Exception {
        try {
            String keyForModel = SpConstant.ORG.REDIS_KEY_PREFIX_BUYER + buyerVisibilityModel.getBuyerId();
            jedisManager.setValueByKey(keyForModel, JSONObject.toJSONString(buyerVisibilityModel), SpConstant.REDIS.BUYER_KEEP_HOURS * 3600);
        } catch (Exception e) {
            throw new RedisBusinessException(String.format("Exception occurred while push buyer visibility model to redis!"));
        }
    }

    @Override
    public CompanyBuyerVisibilityModel pullCompanyBuyerVisibilityModelFromRedis(String buyerId) throws Exception {
        try {
            String keyForModel = SpConstant.ORG.REDIS_KEY_PREFIX_BUYER + buyerId;
            String buyerVisibilityModelInJson = jedisManager.getValueBykey(keyForModel);
            if (StringUtils.isBlank(buyerVisibilityModelInJson)) {
                return null;
            }
            return JSONObject.parseObject(buyerVisibilityModelInJson, CompanyBuyerVisibilityModel.class);
        } catch (Exception e) {
            throw new RedisBusinessException(String.format("Exception occurred while push buyer visibility model to redis!"));
        }
    }

    @Override
    public CompanyBuyerVisibilityModel pullVisibilityModelFromRedisIfNoneGetNew(Integer userId) throws Exception {
        // 根据userId拿到CompanyBuyerVisibilityModel
        CompanyBuyerVisibilityModel buyerVisibilityModel = new CompanyBuyerVisibilityModel();
        SpBuyerInfo sbi = buyerInfoDao.selectByPrimaryKey(userId);
        User user = userDao.selectByPrimaryKey(userId);
        if (sbi == null || user == null) {
            logger.error(String.format("Internal error occured. Please contact SiRP for assistance."));
            throw new BusinessException(String.format("Internal error occured. Please contact SiRP for assistance."));
        }
        String userType = user.getUserType();
        String buyerId = sbi.getBuyerId();
        switch (userType) {
            case SpConstant.USER_TYPE.CORP_BUYER:
            {
                buyerVisibilityModel = pullCompanyBuyerVisibilityModelFromRedis(buyerId);
                // 如果从redis拿不到
                if (buyerVisibilityModel == null) {
                    // 重新生成buyerVisibilityModel
                    SpCompanyBuyerUserModel buyerUserModel = (SpCompanyBuyerUserModel) usersService.findCommonUserModelByUserId(userId);
                    CompanyOrgBuyerModel buyerModel = (CompanyOrgBuyerModel) getCompanyModelFromRedisByUserId(userId, userType);
                    if (buyerUserModel == null || buyerModel == null) {
                        logger.error("Invalid company structure. Please contact SiRP for assistance.");
                        throw new BusinessException("Invalid company structure. Please contact SiRP for assistance.");
                    } else {
                        buyerVisibilityModel = getVisibilityModelForBuyer(userId, buyerUserModel.getRoleId(), buyerUserModel.getBuyerId(), buyerModel);
                        if (buyerVisibilityModel != null) {
                            pushCompanyBuyerVisibilityModelToRedis(buyerVisibilityModel);
                        }
                    }
                }
                return buyerVisibilityModel;
            }
            case SpConstant.USER_TYPE.CORP_SUPPLIER:
            {
                throw new BusinessException(String.format("Invalid user type requested. Please contact SiRP for assistance."));
            }
            default:
                throw new BusinessException(String.format("Invalid user type!"));
        }
    }

    @Transactional(rollbackFor = {Exception.class, BusinessException.class})
    @Override
    public void inviteNewCompanyBuyer(Integer inviterUserId, InviteCompanyBuyerReqVo reqVo) throws Exception {
        // 先检查公司相关的状态 (controller已检查过userId和userType)
        // 检查inviterUserId是不是公司的superAdmin   后续注册验证
        SpSysCompanySuperuserRef superuserRef = superuserRefDao.selectByPrimaryKey(inviterUserId);
        if (superuserRef == null) {
            logger.error(String.format("Inviter [userId=%s] is not a super user in the company [%s]!", inviterUserId, reqVo.getSysCompanyId()));
            throw new BusinessException(String.format("Limited permission to invite new users. Please contact your admin."));
        }
        SpBuyerInfo sbi = buyerInfoDao.selectByPrimaryKey(inviterUserId);
        if (sbi == null) {
            logger.error(String.format("No buyer info for inviter[%S]", inviterUserId));
            throw new BusinessException(String.format("Incomplete user information. Please complete your information."));
        }
        // 检查email是否被占用     后续注册验证
        String email = reqVo.getEmail();
        UserExample userExample = new UserExample();
        userExample.createCriteria().andUsernameEqualTo(email);
        List<User> users = userDao.selectByExample(userExample);
        if (!CollectionUtils.isEmpty(users)) {
            logger.error(String.format("Email[%s] already exists. Please try another.", email));
            throw new BusinessException(String.format("Email[%s] already exists. Please try another.", email));
        }
        // 检查 sysCompanyId和targetOrgCode是否都存在 是否关联          后续注册验证
        String sysCompanyId = reqVo.getSysCompanyId();
        String targetOrgCode = reqVo.getOrgCodeToMount();
        SpOrgInfo orgInfo = orgInfoDao.selectByPrimaryKey(targetOrgCode);
        if (orgInfo == null) {
            logger.error(String.format("Org code[%s] not exist!", targetOrgCode));
            throw new BusinessException(String.format("Invalid team action requested. Please contact SiRP for assistance."));
        } else if (!sysCompanyId.equalsIgnoreCase(orgInfo.getSysCompanyId())) {
            logger.error(String.format("Org code[%s] not mapped to  sysCompanyId[%s]!", targetOrgCode, sysCompanyId));
            throw new BusinessException(String.format("Invalid team action requested. Please contact SiRP for assistance.", targetOrgCode));
        }
        SpSysCompanyInfo ssci = sysCompanyInfoDao.selectByPrimaryKey(sysCompanyId);
        if (ssci == null) {
            logger.error(String.format("SysCompanyId [%s] not exist!", sysCompanyId));
            throw new BusinessException(String.format("Company not exist!"));
        }
        // 检查该email之前是不是已经邀请过
        SpSysCompanyPendingInvitationExample invitationExample = new SpSysCompanyPendingInvitationExample();
        invitationExample.createCriteria().andSysCompanyIdEqualTo(sysCompanyId).andPendingUserEmailEqualTo(email);
        List<SpSysCompanyPendingInvitation> pendingInvitations = pendingInvitationDao.selectByExample(invitationExample);
        if (pendingInvitations != null && pendingInvitations.size() > 0) {
            logger.error(String.format("This email[%s] is already in the pending invitation list!", email));
            throw new BusinessException(String.format("This email[%s] is already in the pending invitation list!", email));
        }
        // 检查该公司的membership是否在active或者trial状态           后续注册验证
        SpSysCompanyMembership companyMembership = sysCompanyMembershipDao.selectByPrimaryKey(sysCompanyId);
        if (companyMembership == null) {
            logger.error(String.format("SysCompanyId [%s] not activated!", sysCompanyId));
            throw new BusinessException(String.format("Company not activated!"));
        } else if (!SpConstant.MEMBERSHIP.FLAG.ACTIVE.equals(companyMembership.getMembershipFlag())
                && !SpConstant.MEMBERSHIP.FLAG.TRIAL.equals(companyMembership.getMembershipFlag())) {
            logger.error(String.format("SysCompanyId [%s] not activated!", sysCompanyId));
            throw new BusinessException(String.format("Company not activated!"));
        } else {
            Integer maxUserNum = companyMembership.getMaxUserNum() != null ? companyMembership.getMaxUserNum() : SpConstant.MEMBERSHIP.DEFAULT_COMPANY_MAX_USER;
            Integer currentUserNum = companyMembership.getCurrentUserNum() != null ? companyMembership.getCurrentUserNum() : 0;
            SpSysCompanyPendingInvitationExample invitationExample1 = new SpSysCompanyPendingInvitationExample();
            invitationExample1.createCriteria().andSysCompanyIdEqualTo(sysCompanyId);
            Long pendingInvitationLong = pendingInvitationDao.countByExample(invitationExample1);
            if ((currentUserNum + pendingInvitationLong.intValue()) < maxUserNum) {
                // 验证结束 开始邀请
                // Step 1: 添加邀请记录
                Date now = new Date();
                SpSysCompanyPendingInvitation pendingInvitation = new SpSysCompanyPendingInvitation();
                pendingInvitation.setSysCompanyId(sysCompanyId);
                pendingInvitation.setPendingUserEmail(email);
                pendingInvitation.setCreateTime(now);
                pendingInvitation.setOrgCode(reqVo.getOrgCodeToMount());
                pendingInvitation.setOrgName(orgInfo.getOrgName());
                pendingInvitation.setIsAdmin(reqVo.getIsAdmin());
                pendingInvitation.setIsSuperAdmin(reqVo.getIsSuperAdmin());
                pendingInvitation.setMappingType(reqVo.getMappingType());
                pendingInvitation.setMountStatus(reqVo.getMountStatus());
                pendingInvitation.setUserType(reqVo.getUserType());
                pendingInvitation.setInviterUserId(inviterUserId);
                pendingInvitationDao.insertSelective(pendingInvitation);
                // step 2: 写入用户操作记录
                SpUserActionRec actionRec = new SpUserActionRec();
                actionRec.setActionType("1".equals(reqVo.getIsAdmin()) ?
                        SpConstant.USER_ACTION_TYPE.ORG.MEMBER.BUYER.INVITE_ADMIN_SEND :
                        SpConstant.USER_ACTION_TYPE.ORG.MEMBER.BUYER.INVITE_REGULAR_USER_SEND);
                actionRec.setUserId(inviterUserId);
                actionRec.setSysCompanyId(sysCompanyId);
                actionRec.setSysSupplierName(ssci.getEntityName());
                actionRec.setUpdateTime(now);
                actionRec.setNotes("Invite user: " + reqVo.getEmail());
                spUserActionRecDao.insert(actionRec);
                // Step 3: 发送邀请邮件
                String invitationId = pendingInvitation.getInvitationId();
                String useSsl = PropertiesUtil.getProperty("sirp.enable.https");
                // 根据inviterUserId拿到inviter的相关信息
                String inviterName = sbi.getLastname() + ", " + sbi.getFirstname();
                String jobtitle = StringUtils.isNotBlank(sbi.getJobtitle()) ? sbi.getJobtitle() : "";
                String orgName = orgInfo.getOrgName();
                String sysCompanyName = orgInfo.getSysCompanyName();
                spMailUtil.sendCompanyUserInvitationEmail(invitationId, reqVo.getUserType(),
                        inviterName, jobtitle, orgName, sysCompanyName,
                        email, "true".equalsIgnoreCase(useSsl) ? true : false);
            } else {
                // 用户已满 无法添加更多用户
                logger.error(String.format("Number of current user [%d] (with %d pending invitations) in this company exceeds the limit[%d]!", currentUserNum, pendingInvitationLong.intValue(), maxUserNum));
                throw new BusinessException(String.format("Cannot add more user in this company. Number of user exceeds the limit!"));
            }
        }
    }

    @Transactional(rollbackFor = {Exception.class, BusinessException.class})
    @Override
    public void undoInviteNewCompanyBuyer(Integer operatorUserId, UndoInviteCompanyBuyerReqVo reqVo) throws Exception {
        // 先检查公司相关的状态 (controller已检查过userId和userType)
        // 检查inviterUserId是不是公司的superAdmin   后续注册验证
        SpSysCompanySuperuserRef superuserRef = superuserRefDao.selectByPrimaryKey(operatorUserId);
        if (superuserRef == null) {
            logger.error(String.format("Inviter [userId=%s] is not a super user in the company [%s]!", operatorUserId, reqVo.getSysCompanyId()));
            throw new BusinessException(String.format("Limited permission to invite new users. Please contact your admin."));
        }
        SpBuyerInfo sbi = buyerInfoDao.selectByPrimaryKey(operatorUserId);
        if (sbi == null) {
            logger.error(String.format("No buyer info for inviter[%S]", operatorUserId));
            throw new BusinessException(String.format("Incomplete user information. Please complete your information."));
        }
        // 检查 sysCompanyId和targetOrgCode是否都存在 是否关联          后续注册验证
        String sysCompanyId = reqVo.getSysCompanyId();
        String targetOrgCode = reqVo.getOrgCodeToMount();
        SpOrgInfo orgInfo = orgInfoDao.selectByPrimaryKey(targetOrgCode);
        if (orgInfo == null) {
            logger.error(String.format("Org code [%s] not exist!", targetOrgCode));
            throw new BusinessException(String.format("Invalid organization!"));
        }
        SpSysCompanyInfo ssci = sysCompanyInfoDao.selectByPrimaryKey(sysCompanyId);
        if (ssci == null) {
            logger.error(String.format("SysCompanyId [%s] not exist!", sysCompanyId));
            throw new BusinessException(String.format("Company not exist!"));
        }
        // 检查该公司的membership是否在active或者trial状态           后续注册验证
        SpSysCompanyMembership companyMembership = sysCompanyMembershipDao.selectByPrimaryKey(sysCompanyId);
        if (companyMembership == null) {
            logger.error(String.format("SysCompanyId [%s] not activated!", sysCompanyId));
            throw new BusinessException(String.format("Company not activated!"));
        } else if (!SpConstant.MEMBERSHIP.FLAG.ACTIVE.equals(companyMembership.getMembershipFlag())
                && !SpConstant.MEMBERSHIP.FLAG.TRIAL.equals(companyMembership.getMembershipFlag())) {
            logger.error(String.format("SysCompanyId [%s] not activated!", sysCompanyId));
            throw new BusinessException(String.format("Company not activated!"));
        } else {
            String invitationId = reqVo.getInvitationId();
            SpSysCompanyPendingInvitation pendingInvitation = pendingInvitationDao.selectByPrimaryKey(invitationId);
            if (pendingInvitation != null) {
                pendingInvitationDao.deleteByPrimaryKey(invitationId);
            } else {
                logger.error(String.format("Invitation Id [%s] not found!", invitationId));
                throw new BusinessException(String.format("Invalid Invitation!"));
            }
        }
    }
    @Transactional(rollbackFor = {Exception.class, BusinessException.class})
    @Override
    public void addNewBuyerOrgNode(Integer userId, AddNewBuyerOrgNodeReqVo reqVo) throws Exception {
        // 检查inviterUserId是不是公司的superAdmin   后续注册验证
        SpSysCompanySuperuserRef superuserRef = superuserRefDao.selectByPrimaryKey(userId);
        if (superuserRef == null) {
            logger.error(String.format("Inviter [userId=%s] is not a super user in the company [%s]!", userId, reqVo.getSysCompanyId()));
            throw new BusinessException(String.format("Limited permission to invite new users. Please contact your admin."));
        }
        String sysCompanyId = reqVo.getSysCompanyId();
        String parentOrgCode = reqVo.getParentOrgCode();
        String orgName = reqVo.getOrgName();
        String mappingType = reqVo.getMappingType(); // 03
        String orgStatus = reqVo.getOrgStatus();
        String currentDateStr = DateUtil.getCurrentDateStr(DateUtil.FORMAT_YYYYMMDDHHMMSSSSS);
        String orgCode = SpConstant.ORG_CODE_PREFIX + currentDateStr;
        // 检查sysCompanyId是否存在
        SpSysCompanyInfo sysCompanyInfo = sysCompanyInfoDao.selectByPrimaryKey(sysCompanyId);
        if (sysCompanyInfo == null) {
            logger.error(String.format("SysCompanyId[%s] not found!", sysCompanyId));
            throw new BusinessException(String.format("Invalid company!"));
        }
        String sysCompanyName = sysCompanyInfo.getEntityName();
        // 检查parentOrgCode是否存在
        if (SpConstant.ORG.ROOT_ORG_IN_REQ.equalsIgnoreCase(parentOrgCode)) {
            // 将该新节点作为1级节点插入
            SpOrgInfo orgInfo = new SpOrgInfo();
            orgInfo.setOrgCode(orgCode);
            orgInfo.setSysCompanyId(sysCompanyId);
            orgInfo.setSysCompanyName(sysCompanyName);
            orgInfo.setOrgStatus(orgStatus);
            orgInfo.setDelStatus(SpConstant.ORG.DEL_STATUS.NO);
            orgInfo.setParentOrgCode("");
            orgInfo.setOrgName(orgName);
            orgInfo.setOrgLevel(SpConstant.ORG.LEVEL.ROOT);
            orgInfo.setOrgType(mappingType);
            orgInfoDao.insert(orgInfo);
            // 向user_action表中插入记录
            SpUserActionRec actionRec = new SpUserActionRec();
            actionRec.setSysCompanyId(sysCompanyId);
            actionRec.setUserId(userId);
            Date now = new Date();
            actionRec.setUpdateTime(now);
            actionRec.setActionType(SpConstant.USER_ACTION_TYPE.ORG.ADD);
            actionRec.setNotes(String.format("Add New Buyer Org Node %s[%s] whose parentOrgCode is %s[%s]", orgName, orgCode, "ROOT", ""));
            spUserActionRecDao.insert(actionRec);
        } else {
            SpOrgInfo orgInfo = orgInfoDao.selectByPrimaryKey(parentOrgCode);
            if (orgInfo == null) {
                logger.error(String.format("Parent org code[%s] not found!", parentOrgCode));
                throw new BusinessException(String.format("Invalid parental organization specified!"));
            }
            // 将新节点作为子节点插入
            SpOrgInfo orgInfo2 = new SpOrgInfo();
            orgInfo2.setOrgCode(orgCode);
            orgInfo2.setSysCompanyId(sysCompanyId);
            orgInfo2.setSysCompanyName(sysCompanyName);
            orgInfo2.setOrgStatus(orgStatus);
            orgInfo2.setDelStatus(SpConstant.ORG.DEL_STATUS.NO);
            orgInfo2.setParentOrgCode(orgInfo.getParentOrgCode());
            orgInfo2.setOrgName(orgName);
            orgInfo2.setOrgLevel(String.valueOf(Integer.parseInt(orgInfo.getOrgLevel()) + 1));
            orgInfo2.setOrgType(mappingType);
            orgInfoDao.insert(orgInfo2);
            // 向user_action表中插入记录
            SpUserActionRec actionRec = new SpUserActionRec();
            actionRec.setSysCompanyId(sysCompanyId);
            actionRec.setUserId(userId);
            Date now = new Date();
            actionRec.setUpdateTime(now);
            actionRec.setActionType(SpConstant.USER_ACTION_TYPE.ORG.ADD);
            actionRec.setNotes(String.format("Add New Buyer Org Node %s[%s] whose parentOrgCode is %s[%s]", orgName, orgCode, orgInfo.getOrgName(), parentOrgCode));
            spUserActionRecDao.insert(actionRec);
        }
        // 异步刷新CompanyBuyerModel
        asyncRefreshRedisCompanyModelBySysCompanyId(sysCompanyId, SpConstant.SYS_COMPANY.TYPE.BUYER);
    }
    @Transactional(rollbackFor = {Exception.class, BusinessException.class})
    @Override
    public void editBuyerOrgNodeInfo(Integer userId, EditBuyerOrgNodeReqVo reqVo) throws Exception {
        // 检查inviterUserId是不是公司的superAdmin   后续注册验证
        SpSysCompanySuperuserRef superuserRef = superuserRefDao.selectByPrimaryKey(userId);
        if (superuserRef == null) {
            logger.error(String.format("Inviter [userId=%s] is not a super user in the company!", userId));
            throw new BusinessException(String.format("Limited permission to invite new users. Please contact your admin."));
        }
        String orgCode = reqVo.getOrgCode();
        String parentOrgCode = reqVo.getParentOrgCode();
        String orgName = reqVo.getOrgName();
        String mappingType = reqVo.getMappingType(); // 03
        String orgStatus = reqVo.getOrgStatus();
        String delStatus = reqVo.getDelStatus();
        String parentOrgName = "";
        // 检查orgCode是否存在
        SpOrgInfo orgInfo = orgInfoDao.selectByPrimaryKey(orgCode);
        if (orgInfo == null) {
            logger.error(String.format("OrgCode[%s] not found!", orgCode));
            throw new BusinessException(String.format("Invalid organization!"));
        }
        String sysCompanyId = orgInfo.getSysCompanyId();
        String sysCompanyName = orgInfo.getSysCompanyName();
        // 检查sysCompanyId是否存在
        SpSysCompanyInfo sysCompanyInfo = sysCompanyInfoDao.selectByPrimaryKey(sysCompanyId);
        if (sysCompanyInfo == null) {
            logger.error(String.format("SysCompanyId[%s] not found!", sysCompanyId));
            throw new BusinessException(String.format("Invalid company!"));
        }
        // 处理parentOrgCode相关属性parentOrgCode和Level
        if (StringUtils.isNotBlank(parentOrgCode)) {
            if (SpConstant.ORG.ROOT_ORG_IN_REQ.equalsIgnoreCase(parentOrgCode)) {
                orgInfo.setParentOrgCode("");
                orgInfo.setOrgLevel(SpConstant.ORG.LEVEL.ROOT);
            } else {
                // 检查parentOrgCode是否存在
                SpOrgInfo orgInfoParent = orgInfoDao.selectByPrimaryKey(parentOrgCode);
                if (orgInfoParent == null) {
                    logger.error(String.format("ParentOrgCode[%s] not found!", parentOrgCode));
                    throw new BusinessException(String.format("Invalid parental organization!"));
                }
                parentOrgName = orgInfoParent.getOrgName();
                orgInfo.setParentOrgCode(parentOrgCode);
                orgInfo.setOrgLevel(String.valueOf(Integer.parseInt(orgInfoParent.getOrgLevel())+1));
            }
        }
        // 其他属性
        orgInfo.setOrgName(orgName);
        orgInfo.setOrgStatus(orgStatus);
        orgInfo.setDelStatus(delStatus);
        orgInfoDao.updateByPrimaryKey(orgInfo);
        // 写入userAction记录
        SpUserActionRec actionRec = new SpUserActionRec();
        actionRec.setSysCompanyId(sysCompanyId);
        actionRec.setUserId(userId);
        actionRec.setActionType(SpConstant.USER_ACTION_TYPE.ORG.EDIT);
        Date now = new Date();
        actionRec.setUpdateTime(now);
        actionRec.setNotes(String.format("Edit Buyer Org Node %s[%s] whose parentOrgCode is %s[%s]", orgName, orgCode, parentOrgName, orgInfo.getParentOrgCode()));
        spUserActionRecDao.insert(actionRec);
        // 异步刷新
        // 1. companyModel
        asyncRefreshRedisCompanyModelBySysCompanyId(sysCompanyId, SpConstant.SYS_COMPANY.TYPE.BUYER);
        // 2. 将该公司下所有buyer的visibility清空
        SpBuyerInfoExample buyerInfoExample1 = new SpBuyerInfoExample();
        buyerInfoExample1.createCriteria().andSysCompanyIdEqualTo(sysCompanyId);
        List<SpBuyerInfo> buyerInfoList = buyerInfoDao.selectByExample(buyerInfoExample1);
        if (buyerInfoList != null && buyerInfoList.size() > 0) {
            for (SpBuyerInfo buyer : buyerInfoList) {
                String tempKey = SpConstant.ORG.REDIS_KEY_PREFIX_BUYER + buyer.getBuyerId();
                jedisManager.deleteByKey(tempKey);
            }
        }
    }

    @Transactional(rollbackFor = {Exception.class, BusinessException.class})
    @Override
    public void editBuyerOrgNodeMounts(Integer operatorUserId, EditOrgNodeMountBuyerReqVo reqVo) throws Exception{
        // 检查inviterUserId是不是公司的superAdmin   后续注册验证
        SpSysCompanySuperuserRef superuserRef0 = superuserRefDao.selectByPrimaryKey(operatorUserId);
        if (superuserRef0 == null) {
            logger.error(String.format("Inviter [userId=%s] is not a super user in the company!", operatorUserId));
            throw new BusinessException(String.format("Limited permission to invite new users. Please contact your admin."));
        }
        String currentOrgCode = reqVo.getCurrentOrgCode();
        String targetOrgCode = reqVo.getTargetOrgCode();
        String isAdmin = reqVo.getIsAdmin();
        String isSuperAdmin = reqVo.getIsSuperAdmin();
        String buyerId = reqVo.getBuyerId();
        String mountStatus = reqVo.getMountStatus();
        // 检查buyerId是否有效
        SpBuyerInfoExample buyerInfoExample = new SpBuyerInfoExample();
        buyerInfoExample.createCriteria().andBuyerIdEqualTo(buyerId);
        List<SpBuyerInfo> buyerInfos = buyerInfoDao.selectByExample(buyerInfoExample);
        if (buyerInfos != null && buyerInfos.size() == 1) {
            SpBuyerInfo buyerInfo = buyerInfos.get(0);
            // 拿到userId
            Integer userId = buyerInfo.getUserId();
            String sysCompanyId = buyerInfo.getSysCompanyId();
            // 根据userId拿到userRoleRef
            UserRoleRefExample roleRefExample = new UserRoleRefExample();
            roleRefExample.createCriteria().andUserIdEqualTo(userId);
            List<UserRoleRef> roleRefs = userRoleRefDao.selectByExample(roleRefExample);
            if (roleRefs == null || roleRefs.size() != 1) {
                logger.error(String.format("Buyer[%s]'s roleRef info not found!", buyerId));
                throw new BusinessException(String.format("Buyer info not found!"));
            }
            UserRoleRef roleRef = roleRefs.get(0);
            // 根据userId拿到org_user_ref
            SpOrgUserRef orgUserRef = orgUserRefDao.selectByPrimaryKey(userId, SpConstant.ORG.TYPE.BUYER);
            if (orgUserRef == null) {
                logger.error(String.format("Org info for buyer [%s] not found!", buyerId));
                throw new BusinessException(String.format("Organization info not found!"));
            } else if (!currentOrgCode.equals(orgUserRef.getOrgCode())) {
                logger.error(String.format("Org info for buyer [%s] not found!", buyerId));
                throw new BusinessException(String.format("Organization info not found!"));
            }
            orgUserRef.setMountStatus(mountStatus);
            // 在targetOrgCode不为空时 检查其是否存在
            if (StringUtils.isNotBlank(targetOrgCode)) {
                SpOrgInfo targetOrgInfo = orgInfoDao.selectByPrimaryKey(targetOrgCode);
                if (targetOrgInfo == null) {
                    logger.error(String.format("Target Org code [%s] for buyer  not found!", targetOrgCode));
                    throw new BusinessException(String.format("Target organization not found!"));
                } else if (!sysCompanyId.equalsIgnoreCase(targetOrgInfo.getSysCompanyId())) {
                    logger.error(String.format("Target Org code [%s] for buyer  not found!", targetOrgCode));
                    throw new BusinessException(String.format("Target organization not found!"));
                }
                orgUserRef.setOrgCode(targetOrgCode);
                orgUserRef.setOrgName(targetOrgInfo.getOrgName());
            }
            orgUserRefDao.updateByPrimaryKey(orgUserRef);
            // 处理isAdmin
            String roleSuffix = SpConstant.ORG.BUYER.IS_ADMIN.YES.equals(isAdmin) ? SpConstant.ROLE_ID_SUFFIX.ADMIN : SpConstant.ROLE_ID_SUFFIX.USER;
            String roleIdStr = buyerInfo.getRoleId();
            if (roleIdStr.endsWith(roleSuffix)) {
                // do nothing
            } else {
                StringBuffer sb = new StringBuffer(roleIdStr);
                String newRoleId = sb.replace(sb.length()-1, sb.length()-1, roleSuffix).toString(); // TODO test required
                buyerInfo.setRoleId(newRoleId);
                buyerInfoDao.updateByPrimaryKey(buyerInfo);
                roleRef.setRoleId(Integer.parseInt(newRoleId));
                userRoleRefDao.updateByPrimaryKey(roleRef);
            }
            // 处理isSuperAdmin
            if (SpConstant.ORG.BUYER.IS_SUPER_ADMIN.YES.equals(isSuperAdmin)) {
                SpSysCompanySuperuserRef superuserRef = superuserRefDao.selectByPrimaryKey(userId);
                // 如没有 添加
                if (superuserRef == null) {
                    superuserRef = new SpSysCompanySuperuserRef();
                    superuserRef.setUserId(userId);
                    superuserRef.setSysCompanyId(sysCompanyId);
                    superuserRef.setCreateTime(new Date());
                    superuserRefDao.insert(superuserRef);
                } else {
                    if (!sysCompanyId.equals(superuserRef.getSysCompanyId())) {
                        // 如有 确保sysCompanyId一致
                        superuserRef.setSysCompanyId(sysCompanyId);
                        superuserRefDao.updateByPrimaryKey(superuserRef);
                    }
                }
            } else {
                // 如有 保证至少还剩一个superAdmin 然后删除
                SpSysCompanySuperuserRef superuserRef = superuserRefDao.selectByPrimaryKey(userId);
                if (superuserRef != null) {
                    String sysCompanyIdSuperAdmin = superuserRef.getSysCompanyId();
                    SpSysCompanySuperuserRefExample superuserRefExample = new SpSysCompanySuperuserRefExample();
                    superuserRefExample.createCriteria().andSysCompanyIdEqualTo(sysCompanyIdSuperAdmin);
                    List<SpSysCompanySuperuserRef> superuserRefs = superuserRefDao.selectByExample(superuserRefExample);
                    if (superuserRefs != null && superuserRefs.size() > 1) {
                        superuserRefDao.deleteByPrimaryKey(userId);
                    } else {
                        throw new BusinessException(String.format("At least 1 super user is set for the company!"));
                    }
                }
            }
            // 插入user_action
            SpUserActionRec actionRec = new SpUserActionRec();
            actionRec.setSysCompanyId(sysCompanyId);
            actionRec.setUserId(operatorUserId);
            actionRec.setActionType(SpConstant.USER_ACTION_TYPE.ORG.EDIT);
            Date now = new Date();
            actionRec.setUpdateTime(now);
            actionRec.setNotes(String.format("Edit Node mount buyer [%s]", buyerId));
            spUserActionRecDao.insert(actionRec);
            // 异步refresh
            // 1. companyModel
            asyncRefreshRedisCompanyModelBySysCompanyId(sysCompanyId, SpConstant.SYS_COMPANY.TYPE.BUYER);
            // 2. visibilityModel 将该公司下所有buyer的visibility清空
            SpBuyerInfoExample buyerInfoExample1 = new SpBuyerInfoExample();
            buyerInfoExample1.createCriteria().andSysCompanyIdEqualTo(sysCompanyId);
            List<SpBuyerInfo> buyerInfoList = buyerInfoDao.selectByExample(buyerInfoExample1);
            if (buyerInfoList != null && buyerInfoList.size() > 0) {
                for (SpBuyerInfo buyer : buyerInfoList) {
                    String tempKey = SpConstant.ORG.REDIS_KEY_PREFIX_BUYER + buyer.getBuyerId();
                    jedisManager.deleteByKey(tempKey);
                }
            }
        } else {
            logger.error(String.format("Buyer[%s] info not found!", buyerId));
            throw new BusinessException(String.format("Buyer info not found!"));
        }

    }

    @Override
    public Set<String> getAllBuyerIdsInSysCompany(String sysCompanyId) {
        Set<String> retSet = new HashSet<>();
        SpBuyerInfoExample buyerInfoExample = new SpBuyerInfoExample();
        buyerInfoExample.createCriteria().andSysCompanyIdEqualTo(sysCompanyId);
        List<SpBuyerInfo> buyerInfos = buyerInfoDao.selectByExample(buyerInfoExample);
        if (!CollectionUtils.isEmpty(buyerInfos)) {
            for (SpBuyerInfo buyerInfo : buyerInfos) {
                retSet.add(buyerInfo.getBuyerId());
            }
        }
        return retSet;
    }
//
//    @Transactional(rollbackFor = {Exception.class, BusinessException.class})
//    @Override
//    public void clearBuyerOrgNodeMounts(Integer userId, ClearBuyerOrgNodeMountsReqVo reqVo) throws Exception {
//        String orgCode = reqVo.getOrgCode();
//        SpOrgUserRef operator = orgUserRefDao.selectByPrimaryKey(userId, SpConstant.ORG.TYPE.BUYER);
//        if (operator == null) {
//            logger.error(String.format("Org info for userId [%s] not found!", userId));
//            throw new BusinessException(String.format("Organization info not found!"));
//        }
//        String sysCompanyId = operator.getSysCompanyId();
//
//        SpOrgUserRefExample orgUserRefExample = new SpOrgUserRefExample();
//        orgUserRefExample.createCriteria().andSysCompanyIdEqualTo(sysCompanyId).andMappingTypeEqualTo(SpConstant.ORG.TYPE.BUYER).andOrgCodeEqualTo(orgCode);
//        List<SpOrgUserRef> orgUserRefList = orgUserRefDao.selectByExample(orgUserRefExample);
//        if (orgUserRefList != null && orgUserRefList.size() > 0) {
//            for (SpOrgUserRef ref : orgUserRefList) {
//                Integer tempUserId = ref.getUserId();
//                // 删除buyer
//
//            }
//        }
//
//    }

    private void filterVisibleOrgCodes(BuyerOrgNode thisOrgNode, CompanyOrgBuyerModel buyerModel, Set<String> visibleBuyerIds) {
        Set<String> parentSet = new HashSet<>();
        parentSet.add(thisOrgNode.getOrgCode());
        visibleBuyerIds.addAll(thisOrgNode.getMountAdminBuyerIds());
        visibleBuyerIds.addAll(thisOrgNode.getMountUserBuyerIds());
        getSuccessiveOrgCodesByParent(parentSet, buyerModel, visibleBuyerIds);
    }
//
    private void getSuccessiveOrgCodesByParent(Set<String> parentSet, CompanyOrgBuyerModel buyerModel, Set<String> visibleBuyerIds) {
        Set<String> successiveOrgCodes = new HashSet<>();
        if (parentSet != null && parentSet.size() > 0) {
            for (String parent : parentSet) {
                for (BuyerOrgNode cursor : buyerModel.getBuyerOrgNodes()) {
                    if (parent.equals(cursor.getParentOrgCode())) {
                        visibleBuyerIds.addAll(cursor.getMountAdminBuyerIds());
                        visibleBuyerIds.addAll(cursor.getMountUserBuyerIds());
                        successiveOrgCodes.add(cursor.getOrgCode());
                    }
                }
            }
            // 递归查找
            if (successiveOrgCodes != null && successiveOrgCodes.size() > 0 ) {
                getSuccessiveOrgCodesByParent(successiveOrgCodes, buyerModel, visibleBuyerIds);
            }
        } else {
        }
    }

    private BuyerOrgNode locateThisBuyerInCompanyModel(Integer userId, String roleId, String buyerId, CompanyOrgBuyerModel buyerModel) {
        for (BuyerOrgNode node : buyerModel.getBuyerOrgNodes()) {
            if (node.getMountAdminBuyerIds().contains(buyerId))
                return node;
        }
        return null;
    }


    private Set<String> getMountAdminBuyerIdsByOrgCode(String orgCode) {
        SpOrgUserRefExtFinder finder = new SpOrgUserRefExtFinder();
        finder.setOrgCode(orgCode);
        // 要查询的mount_status
        List<String> mountStatusList = new ArrayList<>();
        mountStatusList.add(SpConstant.ORG.MOUNT_STATUS.YES);
        finder.setMountStatus(mountStatusList);
        // 要查询的role_id
        List<String> roleIdsForAdmin = new ArrayList<>();
        roleIdsForAdmin.add(SpConstant.ROLE_GROUP.CORP_ORG_ADMIN.BUYER.ACTIVE);
        roleIdsForAdmin.add(SpConstant.ROLE_GROUP.CORP_ORG_ADMIN.BUYER.EXPIRED);
        roleIdsForAdmin.add(SpConstant.ROLE_GROUP.CORP_ORG_ADMIN.BUYER.TRIAL);
        roleIdsForAdmin.add(SpConstant.ROLE_GROUP.CORP_ORG_ADMIN.BUYER.VISITOR);
        finder.setRoleId(roleIdsForAdmin);
        return orgUserRefExtDao.getBuyerIdsByOrgCodeRoleIdMountSts(finder);
    }

    private Set<String> getUnmountAdminBuyerIdsByOrgCode(String orgCode) {
        SpOrgUserRefExtFinder finder = new SpOrgUserRefExtFinder();
        finder.setOrgCode(orgCode);
        // 要查询的mount_status
        List<String> mountStatusList = new ArrayList<>();
        mountStatusList.add(SpConstant.ORG.MOUNT_STATUS.NO);
        finder.setMountStatus(mountStatusList);
        // 要查询的role_id
        List<String> roleIdsForAdmin = new ArrayList<>();
        roleIdsForAdmin.add(SpConstant.ROLE_GROUP.CORP_ORG_ADMIN.BUYER.ACTIVE);
        roleIdsForAdmin.add(SpConstant.ROLE_GROUP.CORP_ORG_ADMIN.BUYER.EXPIRED);
        roleIdsForAdmin.add(SpConstant.ROLE_GROUP.CORP_ORG_ADMIN.BUYER.TRIAL);
        roleIdsForAdmin.add(SpConstant.ROLE_GROUP.CORP_ORG_ADMIN.BUYER.VISITOR);
        finder.setRoleId(roleIdsForAdmin);
        return orgUserRefExtDao.getBuyerIdsByOrgCodeRoleIdMountSts(finder);
    }

    private Set<String> getMountUserBuyerIdsByOrgCode(String orgCode) {
        SpOrgUserRefExtFinder finder = new SpOrgUserRefExtFinder();
        finder.setOrgCode(orgCode);
        // 要查询的mount_status
        List<String> mountStatusList = new ArrayList<>();
        mountStatusList.add(SpConstant.ORG.MOUNT_STATUS.YES);
        finder.setMountStatus(mountStatusList);
        // 要查询的role_id
        List<String> roleIdsForAdmin = new ArrayList<>();
        roleIdsForAdmin.add(SpConstant.ROLE_GROUP.CORP_ORG_USER.BUYER.ACTIVE);
        roleIdsForAdmin.add(SpConstant.ROLE_GROUP.CORP_ORG_USER.BUYER.EXPIRED);
        roleIdsForAdmin.add(SpConstant.ROLE_GROUP.CORP_ORG_USER.BUYER.TRIAL);
        roleIdsForAdmin.add(SpConstant.ROLE_GROUP.CORP_ORG_USER.BUYER.VISITOR);
        finder.setRoleId(roleIdsForAdmin);
        return orgUserRefExtDao.getBuyerIdsByOrgCodeRoleIdMountSts(finder);
    }

    private Set<String> getUnmountUserBuyerIdsByOrgCode(String orgCode) {
        SpOrgUserRefExtFinder finder = new SpOrgUserRefExtFinder();
        finder.setOrgCode(orgCode);
        // 要查询的mount_status
        List<String> mountStatusList = new ArrayList<>();
        mountStatusList.add(SpConstant.ORG.MOUNT_STATUS.NO);
        finder.setMountStatus(mountStatusList);
        // 要查询的role_id
        List<String> roleIdsForAdmin = new ArrayList<>();
        roleIdsForAdmin.add(SpConstant.ROLE_GROUP.CORP_ORG_USER.BUYER.ACTIVE);
        roleIdsForAdmin.add(SpConstant.ROLE_GROUP.CORP_ORG_USER.BUYER.EXPIRED);
        roleIdsForAdmin.add(SpConstant.ROLE_GROUP.CORP_ORG_USER.BUYER.TRIAL);
        roleIdsForAdmin.add(SpConstant.ROLE_GROUP.CORP_ORG_USER.BUYER.VISITOR);
        finder.setRoleId(roleIdsForAdmin);
        return orgUserRefExtDao.getBuyerIdsByOrgCodeRoleIdMountSts(finder);
    }


}
