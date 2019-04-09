package com.zr.activiti.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Resource;

import org.activiti.engine.RepositoryService;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.delegate.DelegateTask;
import org.activiti.engine.delegate.TaskListener;
import org.activiti.engine.impl.pvm.process.ActivityImpl;
import org.activiti.engine.runtime.ProcessInstance;
import org.springframework.stereotype.Component;

import com.zr.activiti.entity.CusUserTask;
import com.zr.activiti.service.CusUserTaskService;


/**
 * 使用方法： 在创建流程时，在任务节点上添加一个TaskListener，
 * class填：net.northking.activiti.rest.CusTaskListener,用这种方法需要在代码中重新获取service；
 * 用spring管理下的bean，将监听类交由spring管理， 所以这里选择代理表达式的方式: Delegate
 * Expression:${cusTaskListener}
 * 
 * @author Administrator
 *
 */
@Component
public class CusTaskListener implements TaskListener {
	private static final long serialVersionUID = 1L;

	@Resource
	protected RepositoryService repositoryService;

	@Resource
	private CusUserTaskService cusUserTaskService;

	@Resource
	private RuntimeService runtimeService;

	@Override
	public void notify(DelegateTask delegateTask) {
		setUserTasks(delegateTask);
	}

	
	/**
	 * 设置用户节点处理人
	 * 
	 * @param delegateTask
	 */
	private void setUserTasks(DelegateTask delegateTask) {

		try {
			String processInstanceId = delegateTask.getProcessInstanceId();
			ProcessInstance pi = runtimeService.createProcessInstanceQuery()
					.processInstanceId(processInstanceId).singleResult();
			final String businessKey = pi.getBusinessKey();
			List<CusUserTask> taskList = this.cusUserTaskService.findByProcDefKey(businessKey);
			String taskDefinitionKey = delegateTask.getTaskDefinitionKey();
			
			for (CusUserTask userTask : taskList) {
				String taskKey = userTask.getTaskDefKey();
				if (taskDefinitionKey.equals(taskKey)) {
					setAssigneeToUsersTask(delegateTask, businessKey, userTask);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * 普通用户节点设置特定执行人或候选人<br/>
	 * 只要有一人通过即为通过
	 * @param delegateTask
	 * @param businessKey
	 * @param projectId
	 * @param userTask
	 */
	private void setAssigneeToUsersTask(DelegateTask delegateTask, final String businessKey, 
			CusUserTask userTask) throws Exception{

		String taskType = userTask.getTaskType();
		String userIds = userTask.getCandidate_ids();
		String groupIds = userTask.getGroup_id();
		
		switch (taskType) {
			case CusUserTask.TYPE_ASSIGNEE: {
				System.out.println("CusTaskListener assignee userIds: " + userIds);
				delegateTask.setAssignee(userIds);
				break;
			}
			case CusUserTask.TYPE_CANDIDATEUSER: {
				System.out.println("CusTaskListener 候选用户审批 userIds: " + userIds);
				String[] assigneeIds = null;
				if (StringUtil.isNotEmpty(userIds)) {
					assigneeIds = userIds.split(",");
				}
				List<String> assigneeList = getAssigneeList(delegateTask, assigneeIds);
				if(null == assigneeList)return;
				
				delegateTask.addCandidateUsers(assigneeList);
				break;
			}
			case CusUserTask.TYPE_CANDIDATEGROUP: {
				System.out.println("CusTaskListener 候选组审批 groupIds: " + groupIds);
				/**
				 * 设置候选人，一个通过即为通过 由于我们采用的是项目的用户管理系统，所以这里不能直接设置候选组，
				 * 需要根据groupId到项目的用户系统查询具体的用户然后作为候选人设置到工作流
				 */
				String[] candidateUserIds = getCandidateIds(businessKey, groupIds);
				List<String> assigneeList = getAssigneeList(delegateTask, candidateUserIds);
				if(null == assigneeList)return;
				delegateTask.addCandidateUsers(assigneeList);
				break;
			}
		}
	}


	private List<String> getAssigneeList(DelegateTask delegateTask, String[] candidateUserIds) throws Exception {
		List<String> assigneeList = new ArrayList<>();
		if (null != candidateUserIds) {
			assigneeList = Arrays.asList(candidateUserIds);
		}
		System.out.println("CusTaskListener getAssigneeList candidateUserIds:" + candidateUserIds
				+ ";assigneeList:" + assigneeList.size());
		if (assigneeList.size() < 1) {
			autoPass(delegateTask, delegateTask.getTaskDefinitionKey());
			return null;
		}
		return assigneeList;
	}


	/**
	 * 自动跳过
	 * @param delegateTask
	 * @param taskDefKey
	 * @throws Exception
	 */
	private void autoPass(DelegateTask delegateTask, String taskDefKey) throws Exception {
		ActivityImpl nextNodeInfo = ProcessDefinitionCache.get().getNextNodeInfo(repositoryService,runtimeService,delegateTask.getProcessInstanceId(),taskDefKey);
		System.out.println("CusTaskListener setAssigneeList nextNodeid:"+nextNodeInfo.getId());
		if(nextNodeInfo.getId().contains("reapply")) {
			delegateTask.setVariable(nextNodeInfo.getId(), "false");//reapply_projectManagerAudit等等
		}else {
			delegateTask.setVariable(nextNodeInfo.getId(), "true");//isPass_projectManagerAudit等等
		}
	}

	private String[] getCandidateIds(String businessKey, String groupIds) {
//		final String businessId = businessKey.contains(":") ? businessKey.split(":")[1] : "";//业务id:可能是projectId，也可能是userId等等
		String[] roleCodes = groupIds.split(",");
		return roleCodes;
	}
}
