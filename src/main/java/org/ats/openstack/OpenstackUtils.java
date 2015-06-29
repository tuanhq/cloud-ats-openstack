package org.ats.openstack;





import java.util.ArrayList;
import java.util.concurrent.Future;

import org.ats.openstack.nova.JCloudsNova;
import org.ats.openstack.exception.RegionException;
import org.ats.openstack.keystone.JCloudsKeyStone;
import org.ats.openstack.neutron.JCloudsNeutron;
import org.ats.openstack.neutron.NeutronException;
import org.jclouds.openstack.keystone.v2_0.domain.Tenant;
import org.jclouds.openstack.keystone.v2_0.domain.User;
import org.jclouds.openstack.neutron.v2.domain.Network;
import org.jclouds.openstack.neutron.v2.domain.Router;
import org.jclouds.openstack.neutron.v2.domain.Subnet;
import org.jclouds.openstack.nova.v2_0.domain.FloatingIP;
import org.jclouds.openstack.nova.v2_0.domain.Server;
import org.jclouds.openstack.nova.v2_0.domain.ServerCreated;



public class OpenstackUtils {
  /**
   * 
   * @param tenantName
   * @param userName
   * @param password
   * @param adminEmail
   * @param cidr
   * @throws RegionException
   * @throws NeutronException
   */
public static void createInitialCompanySystem(String tenantName, String userName, String password, String adminEmail, String cidr) throws RegionException, NeutronException{
    
    //Create tenant and user
    JCloudsKeyStone jCloudsKeyStone = OpenstackFactory.getJCloudsKeyStoneInstance();
    Tenant testTenant = jCloudsKeyStone.createTenant(tenantName, tenantName, true);
    User user = jCloudsKeyStone.createUser(testTenant.getId(), userName, password, adminEmail, true);
    String roleDefaultId = jCloudsKeyStone.getRoleIdByName(OpenstackConfiguration.DEFAULT_ROLE);
    jCloudsKeyStone.addRoleOnTenant(testTenant.getId(), user.getId(), roleDefaultId);
    
    //creat network 
    JCloudsNeutron jCloudsNeutron = OpenstackFactory.getJCloudsNeutronInstance(tenantName, userName, password);
    Network network = jCloudsNeutron.createNetwork(tenantName + "_net");
    
    Subnet subnet = jCloudsNeutron.createSubnet(tenantName + "_subnet", network.getId(), cidr == null ? "192.168.1.0/24": cidr );
    //create router 
    String externalNetworkID = jCloudsNeutron.getNetworkIdByName(OpenstackConfiguration.EXTERNAL_NETWORK);
    Router router = jCloudsNeutron.createRouter(tenantName + "_router", externalNetworkID); 
    jCloudsNeutron.addRouterToInterface(router.getId(), subnet.getId());
    
  }

public static void createCompanySystemVM(String tenantName, String userName, String password ) throws Exception {
  
  JCloudsNova jCloudNova = OpenstackFactory.getJCloudsNovaInstance(tenantName, userName, password);
  JCloudsNeutron jCloudNeutron = OpenstackFactory.getJCloudsNeutronInstance(tenantName, userName, password);
  
  String normalName = new StringBuilder().append(tenantName).append("-system").toString();
  String name = new StringBuilder(normalName).append("-").append(tenantName).toString();
  
  String networkId = jCloudNeutron.getNetworkIdByName(tenantName + "_net");
  
  Future<ServerCreated> serverCreate = jCloudNova.createSystemVM(name, networkId);
  
  //Future<OperationStatusResponse> response = azureClient.createSystemVM(name);
//  Logger.info("Submited request to create system vm");
  while (!serverCreate.isDone()) {
    System.out.print('.');
    Thread.sleep(6000);
  }
  //OperationStatusResponse status = response.get();
  
  ServerCreated serverCreated = serverCreate.get();
  
  
  Server server =  jCloudNova.getServerByName(name);
  ArrayList<FloatingIP> listFloatingIpAvailable = jCloudNova.listFloatingIpAvailable(); 
  String floatingIpAddr = null;
  if( listFloatingIpAvailable == null||listFloatingIpAvailable.size()==0){
    floatingIpAddr = jCloudNeutron.createFloatingIP().getFloatingIpAddress();      
  }else{
    floatingIpAddr = listFloatingIpAvailable.get(0).getIp();
  }
  jCloudNova.addFloatingIpToServer(floatingIpAddr, serverCreated.getId());
  
 /* String privateIp = jCloudNova.getFirstIpOfServer(server);
  
  VMModel vmModel = VMHelper.getVMByName(name);
  vmModel.put("public_ip", floatingIpAddr);
  vmModel.put("private_ip", privateIp);
  VMHelper.updateVM(vmModel);
  
//  Logger.info("Create system vm " + name + " has been " + status.getStatus());
  Logger.info("Create system vm " + name + " has been " + server.getStatus());
  
  List<OfferingModel> list = OfferingHelper.getEnableOfferings();
  Collections.sort(list, new Comparator<OfferingModel>() {
    @Override
    public int compare(OfferingModel o1, OfferingModel o2) {
      return o2.getMemory() - o1.getMemory();
    }
  });

  OfferingModel defaultOffering = list.get(0);
  OfferingHelper.addDefaultOfferingForGroup(company.getId(), defaultOffering.getId());
  
  //add to reverse proxy 
  final String vmSystemName = name;
//  final String vmSystemIp = vm.getIPAddress().getHostAddress();
  final String vmSystemIp = floatingIpAddr;
  Thread thread = new Thread(new Runnable() {
    @Override
    public void run() {       
      
      try {
        
      //add guacamole to reverse proxy   
        
        Logger.debug("VMName:" + vmSystemName + " ip:" + vmSystemIp );
        Session session = SSHClient.getSession("127.0.0.1", 22, VMHelper.getSystemProperty("default-user"), VMHelper.getSystemProperty("default-password"));
        ChannelExec channel = (ChannelExec) session.openChannel("exec");
        
        String command = "sudo -S -p '' /etc/nginx/sites-available/manage_location.sh "
            + vmSystemIp + " " + vmSystemName + " 0";
        Logger.info("Command add to reverse proxy:" + command);    
        channel.setCommand(command);
        OutputStream out = channel.getOutputStream();
        channel.connect();

        out.write((VMHelper.getSystemProperty("default-password") + "\n").getBytes());
        out.flush();
        channel.disconnect();
        
        
        //restart service nginx
        channel = (ChannelExec) session.openChannel("exec");
        command = "sudo -S -p '' service nginx restart";              
        Logger.info("Command restart service nginx:" + command);    
        channel.setCommand(command);
        out = channel.getOutputStream();
        channel.connect();

        out.write((VMHelper.getSystemProperty("default-password") + "\n").getBytes());
        out.flush();
        SSHClient.printOut(System.out, channel);
        channel.disconnect();
        
        
       //add jenkin to reverse proxy
        
        if (SSHClient.checkEstablished(vmSystemIp, 22, 300)) {
          session = SSHClient.getSession(vmSystemIp, 22, VMHelper.getSystemProperty("default-user"), VMHelper.getSystemProperty("default-password"));
          channel = (ChannelExec) session.openChannel("exec");
          
          channel = (ChannelExec) session.openChannel("exec");
          command = "sudo -S -p '' /etc/guacamole/change_prefix_jenkin.sh " + vmSystemName;
          Logger.info("Command add to reverse proxy:" + command);    
          channel.setCommand(command);
          out = channel.getOutputStream();
          channel.connect();

          out.write((VMHelper.getSystemProperty("default-password") + "\n").getBytes());
          out.flush();
          SSHClient.printOut(System.out, channel);
          channel.disconnect();
          
          //restart jenkins service
          channel = (ChannelExec) session.openChannel("exec");
          command = "sudo -S -p '' service jenkins restart";                
          Logger.info("Command  restart service jenkins:" + command);    
          channel.setCommand(command);
          out = channel.getOutputStream();
          channel.connect();

          out.write((VMHelper.getSystemProperty("default-password") + "\n").getBytes());
          out.flush();
          SSHClient.printOut(System.out, channel);
          channel.disconnect();

        }          
        session.disconnect();         
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  });
  thread.start();*/
  
}

  public static void main(String[] args) {

    // createInitialCompanySystem("gsoft","gsoft","123456","gsoft@domain.com",null);
    try {
      createCompanySystemVM("gsoft", "gsoft", "123456");
    } catch (Exception e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }

  }
}
