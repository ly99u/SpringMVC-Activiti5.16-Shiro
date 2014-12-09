package com.zml.oa.action;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpSession;
import javax.validation.Valid;

import org.activiti.engine.ActivitiException;
import org.activiti.engine.HistoryService;
import org.activiti.engine.IdentityService;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.TaskService;
import org.activiti.engine.runtime.ProcessInstance;
import org.activiti.engine.task.Task;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.zml.oa.entity.CommentVO;
import com.zml.oa.entity.ExpenseAccount;
import com.zml.oa.entity.SalaryAdjust;
import com.zml.oa.entity.User;
import com.zml.oa.service.IProcessService;
import com.zml.oa.service.ISalaryAdjustService;
import com.zml.oa.service.IUserService;
import com.zml.oa.util.UserUtil;

/**
 * 薪资调整控制类
 * @author ZML
 *
 */

@Controller
@RequestMapping("/salaryAction")
public class SalaryAction {
	private static final Logger logger = Logger.getLogger(ExpenseAction.class);
	
	@Autowired
	private ISalaryAdjustService saService;
	
	@Autowired
	protected RuntimeService runtimeService;
	
    @Autowired
    protected IdentityService identityService;
    
    @Autowired
    protected HistoryService historyService;
    
    @Autowired
    protected TaskService taskService;
	
	@Autowired
	private IUserService userService;
	
	@Autowired
	private IProcessService processService;
	
	
	/**
	 * 跳转添加页面
	 * @param model
	 * @return
	 */
	@RequestMapping(value = "/toAdd", method = RequestMethod.GET)
	public ModelAndView toAdd(Model model){
		if(!model.containsAttribute("salary")) {
            model.addAttribute("salary", new SalaryAdjust());
        }
		return new ModelAndView("salary/add_salary").addObject(model);
	}

	/**
	 * 详细信息
	 * @param id
	 * @param model
	 * @return
	 * @throws Exception
	 */
	@RequestMapping(value="/details/{id}", method = RequestMethod.GET)
	public String details(@PathVariable("id") Integer id, Model model) throws Exception{
		SalaryAdjust salaryAd = this.saService.findById(id);
		model.addAttribute("salary", salaryAd);
		return "/salary/details_salary";
	}
	
	/**
	 * 添加并启动薪资调整流程
	 * @param salary
	 * @param results
	 * @param redirectAttributes
	 * @param session
	 * @param model
	 * @return
	 * @throws Exception
	 */
	@RequestMapping(value = "/doAdd", method = RequestMethod.POST)
	public String doAdd(
			@ModelAttribute("salary") @Valid SalaryAdjust salary,BindingResult results, 
			RedirectAttributes redirectAttributes, 
			HttpSession session, 
			Model model) throws Exception{
        User user = UserUtil.getUserFromSession(session);
        
        if(results.hasErrors()){
        	model.addAttribute("salary", salary);
        	return "salary/add_salary";
        }
        
        // 用户未登录不能操作，实际应用使用权限框架实现，例如Spring Security、Shiro等
        if (user == null || user.getId() == null) {
        	model.addAttribute("msg", "登录超时，请重新登录!");
            return "login";
        }
        
        salary.setApplyDate( new Date() );
        salary.setUserId(user.getId());
        salary.setUser_name(user.getName());
        salary.setTitle(user.getName()+" 的薪资调整申请");
        salary.setBusinessType(SalaryAdjust.SALARY);
        salary.setStatus(SalaryAdjust.PENDING);
        this.saService.doAdd(salary);
        String businessKey = salary.getId().toString();
        salary.setBusinessKey(businessKey);
        try{
        	String processInstanceId = this.processService.startSalaryAdjust(salary);
            redirectAttributes.addFlashAttribute("message", "流程已启动，流程ID：" + processInstanceId);
            logger.info("processInstanceId: "+processInstanceId);
        }catch (ActivitiException e) {
            if (e.getMessage().indexOf("no processes deployed with key") != -1) {
                logger.warn("没有部署流程!", e);
                redirectAttributes.addFlashAttribute("error", "没有部署流程，请在[工作流]->[流程管理]页面点击<重新部署流程>-待完成");
            } else {
                logger.error("启动报销流程失败：", e);
                redirectAttributes.addFlashAttribute("error", "系统内部错误！");
            }
        } catch (Exception e) {
            logger.error("启动报销流程失败：", e);
            redirectAttributes.addFlashAttribute("error", "系统内部错误！");
        } finally {
            identityService.setAuthenticatedUserId(null);
        }
        return "redirect:/salaryAction/toAdd";
	}
	
	/**
     * 审批报销流程
     * @param taskId
     * @param model
     * @return
     * @throws NumberFormatException
     * @throws Exception
     */
    @RequestMapping("/toApproval/{taskId}")
    public String toApproval(@PathVariable("taskId") String taskId, Model model) throws NumberFormatException, Exception{
    	Task task = this.taskService.createTaskQuery().taskId(taskId).singleResult();
		// 根据任务查询流程实例
    	String processInstanceId = task.getProcessInstanceId();
		ProcessInstance pi = this.runtimeService.createProcessInstanceQuery().processInstanceId(processInstanceId).singleResult();
		ExpenseAccount expense = (ExpenseAccount) this.runtimeService.getVariable(pi.getId(), "entity");
		expense.setTask(task);
		List<CommentVO> commentList = this.processService.getComments(pi.getId());
		model.addAttribute("commentList", commentList);
		model.addAttribute("expense", expense);
    	return "salary/audit_salary";
    }
    
    
}
