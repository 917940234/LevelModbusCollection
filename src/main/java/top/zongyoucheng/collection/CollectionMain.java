package top.zongyoucheng.collection;

import com.alibaba.druid.pool.DruidDataSourceFactory;
import com.serotonin.modbus4j.ModbusFactory;
import com.serotonin.modbus4j.ModbusMaster;
import com.serotonin.modbus4j.exception.ModbusTransportException;
import com.serotonin.modbus4j.msg.ReadHoldingRegistersRequest;
import com.serotonin.modbus4j.msg.ReadHoldingRegistersResponse;
import com.serotonin.modbus4j.serial.SerialPortWrapper;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.handlers.BeanHandler;
import org.apache.commons.dbutils.handlers.ScalarHandler;

import javax.sql.DataSource;
import java.sql.Connection;
import java.text.SimpleDateFormat;
import java.util.Properties;

/**
 * 通过串口解析MODBUS协议
 * @author zongyoucheng
 */
public class CollectionMain {
    // 设定MODBUS网络上从站地址
    private final static int SLAVE_ADDRESS_COM3 = 1;
    private final static int SLAVE_ADDRESS_COM4 = 1;
    //串行波特率
    private final static int BAUD_RATE = 9600;

    public static void main(String[] args) throws Exception {

        SerialPortWrapper serialParametersCom3 = new top.zongyoucheng.collection.SerialPortWrapperImpl("COM3", BAUD_RATE, 8, 1, 0, 0, 0);
        SerialPortWrapper serialParametersCom4 = new top.zongyoucheng.collection.SerialPortWrapperImpl("COM4", BAUD_RATE, 8, 1, 0, 0, 0);
        /* 创建ModbusFactory工厂实例 */
        ModbusFactory modbusFactory = new ModbusFactory();
        /* 创建ModbusMaster实例 */
        ModbusMaster masterCom3 = modbusFactory.createRtuMaster(serialParametersCom3);
        ModbusMaster masterCom4 = modbusFactory.createRtuMaster(serialParametersCom4);
        //创建Properties对象
        Properties pros = new Properties();
        //读取druid.properties中参数
        pros.load(ClassLoader.getSystemClassLoader().getResourceAsStream("druid.properties"));
        //创建指定参数的Druid连接池
        DataSource source = DruidDataSourceFactory.createDataSource(pros);
        //创建连接实例
        Connection conn = source.getConnection();
        //创建runner实例
        QueryRunner runner = new QueryRunner();

        double equipmentA1, equipmentA2, equipmentA3, equipmentA6, equipmentA7, equipmentA8;
        //状态标志位，不能被循环重置
        int A1StatusFlag = 1,A2StatusFlag = 1,A3StatusFlag = 1,A6StatusFlag = 1,A7StatusFlag = 1,A8StatusFlag = 1;
        //出铁开始/结束检测标志位，不能被循环重置，用于控制出钢开始/结束的检测模块的运行，确保其只运行一次
        boolean A1PourStartOrEnd = true,A2PourStartOrEnd = true,A3PourStartOrEnd = true,A6PourStartOrEnd = true,A7PourStartOrEnd = true,A8PourStartOrEnd = true;
        //出铁检测标志位，不能被循环重置，用于控制出钢是否结束的检测模块的运行，确保其只运行一次
        int A1PourFlag = 0,A2PourFlag = 0,A3PourFlag = 0,A6PourFlag = 0,A7PourFlag = 0,A8PourFlag = 0;
        //开始出铁时间，作为全局变量方便随时计算，用于计算当前出铁时长
        long A1castStartTime = 0L, A2castStartTime = 0L, A3castStartTime = 0L, A6castStartTime = 0L, A7castStartTime = 0L, A8castStartTime = 0L;
        //初始化状态变量
        double A1statusLowerMark = 0.2;//已根据历史数据初次设定
        double A2statusLowerMark = 0.2;//已根据历史数据初次设定
        double A3statusLowerMark = 0.2;//已根据历史数据初次设定
        double A6statusLowerMark = 0.2;//已根据历史数据初次设定
        double A7statusLowerMark = 0.2;//已根据历史数据初次设定
        double A8statusLowerMark = 0.2;//已根据历史数据初次设定
        //初始化重量变量
        String A1Weight = null, A2Weight = null, A3Weight = null, A6Weight = null, A7Weight = null, A8Weight = null;

        /* 读取液位并显示至数据库 */
        while (true) {
            try {
                //时间测试
                //Long testTime = System.currentTimeMillis();

                masterCom3.init();
                masterCom4.init();
                short[] listCom3 = readHoldingRegistersTest(masterCom3, SLAVE_ADDRESS_COM3);
                short[] listCom4 = readHoldingRegistersTest(masterCom4, SLAVE_ADDRESS_COM4);

                //初始化历史液位变量（180s前液位，用于判断出钢是否结束）
                Double A1HistoryLevel, A2HistoryLevel, A3HistoryLevel, A6HistoryLevel, A7HistoryLevel, A8HistoryLevel;
                //初始化流速流量变量
                String A1Flow, A2Flow, A3Flow, A6Flow, A7Flow, A8Flow;

                equipmentA1 = ((int) (listCom3 != null ? listCom3[0] : 0) - 4000) * 0.000578125;//西出铁口40009寄存器
                equipmentA2 = ((int) (listCom3 != null ? listCom3[1] : 0) - 4000) * 0.000578125;//西出铁口40010寄存器
                equipmentA3 = ((int) (listCom3 != null ? listCom3[2] : 0) - 4000) * 0.000578125;//西出铁口40011寄存器
                equipmentA6 = ((int) (listCom4 != null ? listCom4[3] : 0) - 4000) * 0.000578125;//东出铁口40009寄存器
                equipmentA7 = ((int) (listCom4 != null ? listCom4[2] : 0) - 4000) * 0.000578125;//东出铁口40011寄存器
                equipmentA8 = ((int) (listCom4 != null ? listCom4[0] : 0) - 4000) * 0.000578125;//东出铁口40012寄存器
                //20230106，调换6和8

                //初始化液位变量并写入当前液位
                double A1Level = equipmentA1;
                double A2Level = equipmentA2;
                double A3Level = equipmentA3;
                double A6Level = equipmentA6;
                double A7Level = equipmentA7;
                double A8Level = equipmentA8;

                //初始化时间变量
                Integer equipmentLocationA1 = 1, equipmentLocationA2 = 2, equipmentLocationA3 = 3, equipmentLocationA6 = 6, equipmentLocationA7 = 7, equipmentLocationA8 = 8;//当前铁水包位置
                String castEndTime;//出铁结束时间
                String castTonnage;//最后的出铁吨位

                //-------------------------------------------------A1核心逻辑--------------------------------------------
                //空包模块逻辑-----------------------------------------------液位低于下限且先前A1状态标志位是出铁或满包状态
                if ((A1Level <= A1statusLowerMark) && (A1StatusFlag != 0)) {
                    A1StatusFlag = 0;//A1状态标志位 0，此时进入空包状态，不会在空包状态下重复执行本方法
                    A1PourFlag = 1;//A1出铁检测标志位 1，进入空包状态后开启出铁检测
                    System.out.println("A1空包模块逻辑-----------------------------------------------液位低于下限且先前A1状态标志位是出铁或满包状态");//调试输出
                }
                //满包报警模块逻辑--------------------------------------------------液位在2.5~2.6之间，液位如果真的很高，那也是慢慢升高的，这个区间一定能检测到，但是瞬时3m高度是铁水包本身，不用检测，排除3m以上高度铁水包本身的干扰
                if ((2.5 <= A1Level) && (A1Level <= 2.6) && (A1StatusFlag != 2)) {
                    A1StatusFlag = 2;//A1状态标志位 2，此时进入满包报警状态，不会在满包报警状态下重复执行本方法
                    A1PourFlag = 0;//A1出铁检测标志位 0，进入满包报警状态后关闭出铁检测
                    String sqlCast12 = "UPDATE product_blast_furnace_cast SET cast_alert_time = NOW() WHERE equipment_location = ? ORDER BY blast_furnace_cast_id DESC LIMIT 1";
                    runner.update(conn, sqlCast12, equipmentLocationA1);//更新出铁报警时间
                    System.out.println("A1满包报警模块逻辑--------------------------------------------------液位在2.5~2.6之间时执行, " +
                            "报警时当前液位为：: " + equipmentA1);//调试输出
                }
                //出铁模块逻辑-----------------------------------------------开启出铁检测，出铁标志位在空包时置1且当液位不高于2.7m时，高于2.7m自动忽略
                if ((A1PourFlag == 1) && (A1Level <= 2.7)) {
                    //读取历史液位数据，计算180s液位差
                    String sqlHistoryLevel = "SELECT equipment_a1 FROM product_blast_furnace_level ORDER BY blast_furnace_level_id DESC LIMIT 180,1";
                    ScalarHandler<Double> handerHistoryLevel =  new ScalarHandler<Double>("equipment_a1");
                    A1HistoryLevel = runner.query(conn,sqlHistoryLevel,handerHistoryLevel);
                    double A1Speed = A1Level - A1HistoryLevel;
                    System.out.println("A1出铁模块逻辑-----------------------------------------------开启出铁检测" + "180s液位差: " + A1Speed);
                    //检测出铁开始，当180s液位差在0.24~-0.26之间
                    if ((0.24 <= A1Speed) && (A1Speed <= 0.26) && A1PourStartOrEnd) {
                        A1StatusFlag = 1;//A1状态标志位 置出铁
                        A1castStartTime = System.currentTimeMillis();//计算出铁开始时间，给重量液位模块使用，数据库是自动装载的，与本方法无关
                        String sqlCast01 = "INSERT INTO product_blast_furnace_cast(equipment_location)VALUES(?)";
                        BeanHandler<Object> handerCast01 =  new BeanHandler<Object>(CollectionMain.class);
                        runner.insert(conn,sqlCast01,handerCast01,equipmentLocationA1);
                        A1PourStartOrEnd = false;//出铁开始/结束检测标志位取反，变成结束检测，不会重复执行本方法
                        System.out.println("A1检测出铁开始，180s液位差在0.24~-0.26之间, " +
                                "A1castStartTime: " + A1castStartTime);//调试输出
                    }
                    //检测出铁结束，当180s液位差在-0.03~-0.01之间
                    else if ((-0.03 <= A1Speed) && (A1Speed <= -0.01) && !A1PourStartOrEnd) {
                        castTonnage = A1Weight;//读取出铁吨位
                        castEndTime = String.valueOf(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));//读取出铁结束时间
                        String sqlCast20 = "UPDATE product_blast_furnace_cast SET cast_end_time = NOW(), cast_total_time = (SELECT * FROM(SELECT TIMESTAMPDIFF(SECOND,cast_start_time,NOW()) FROM product_blast_furnace_cast WHERE equipment_location = ? ORDER BY blast_furnace_cast_id DESC LIMIT 1) as a), cast_tonnage = ? WHERE equipment_location = ? ORDER BY blast_furnace_cast_id DESC LIMIT 1";
                        runner.update(conn, sqlCast20, equipmentLocationA1, castTonnage, equipmentLocationA1);
                        A1PourStartOrEnd = true;//出铁开始/结束检测标志位取反，变成开始检测，不会重复执行本方法
                        System.out.println("A1检测出铁结束，180s液位差在-0.03~-0.01之间, " +
                                "castTonnage: " + castTonnage + ", A1Level: " + A1Level + ", castEndTime: " + castEndTime);//调试输出
                    }
                }
                //-------------------------------------------------A1核心逻辑--------------------------------------------

                //-------------------------------------------------A2核心逻辑--------------------------------------------
                //空包模块逻辑-----------------------------------------------液位低于下限且先前A2状态标志位是出铁或满包状态
                if ((A2Level <= A2statusLowerMark) && (A2StatusFlag != 0)) {
                    A2StatusFlag = 0;//A2状态标志位 0，此时进入空包状态，不会在空包状态下重复执行本方法
                    A2PourFlag = 1;//A2出铁检测标志位 1，进入空包状态后开启出铁检测
                    System.out.println("A2空包模块逻辑-----------------------------------------------液位低于下限且先前A2状态标志位是出铁或满包状态");//调试输出
                }
                //满包报警模块逻辑--------------------------------------------------液位在2.5~2.6之间，液位如果真的很高，那也是慢慢升高的，这个区间一定能检测到，但是瞬时3m高度是铁水包本身，不用检测，排除3m以上高度铁水包本身的干扰
                if ((2.5 <= A2Level) && (A2Level <= 2.6) && (A2StatusFlag != 2)) {
                    A2StatusFlag = 2;//A2状态标志位 2，此时进入满包报警状态，不会在满包报警状态下重复执行本方法
                    //A2PourFlag = 0;//A2出铁检测标志位 0，进入满包报警状态后关闭出铁检测
                    String sqlCast12 = "UPDATE product_blast_furnace_cast SET cast_alert_time = NOW() WHERE equipment_location = ? ORDER BY blast_furnace_cast_id DESC LIMIT 1";
                    runner.update(conn, sqlCast12, equipmentLocationA2);//更新出铁报警时间
                    System.out.println("A2满包报警模块逻辑--------------------------------------------------液位在2.5~2.6之间时执行, " +
                            "报警时当前液位为：: " + equipmentA2);//调试输出
                }
                //出铁模块逻辑-----------------------------------------------开启出铁检测，出铁标志位在空包时置1且当液位不高于2.7m时，高于2.7m自动忽略
                if ((A2PourFlag == 1) && (A2Level <= 2.7)) {
                    //读取历史液位数据，计算180s液位差
                    String sqlHistoryLevel = "SELECT equipment_A2 FROM product_blast_furnace_level ORDER BY blast_furnace_level_id DESC LIMIT 180,1";
                    ScalarHandler<Double> handerHistoryLevel =  new ScalarHandler<Double>("equipment_A2");
                    A2HistoryLevel = runner.query(conn,sqlHistoryLevel,handerHistoryLevel);
                    double A2Speed = A2Level - A2HistoryLevel;
                    System.out.println("A2出铁模块逻辑-----------------------------------------------开启出铁检测" + "180s液位差: " + A2Speed);
                    //检测出铁开始，当180s液位差在0.24~-0.26之间
                    if ((0.24 <= A2Speed) && (A2Speed <= 0.26) && A2PourStartOrEnd) {
                        A2StatusFlag = 1;//A2状态标志位 置出铁
                        A2castStartTime = System.currentTimeMillis();//计算出铁开始时间，给重量液位模块使用，数据库是自动装载的，与本方法无关
                        String sqlCast01 = "INSERT INTO product_blast_furnace_cast(equipment_location)VALUES(?)";
                        BeanHandler<Object> handerCast01 =  new BeanHandler<Object>(CollectionMain.class);
                        runner.insert(conn,sqlCast01,handerCast01,equipmentLocationA2);
                        A2PourStartOrEnd = false;//出铁开始/结束检测标志位取反，变成结束检测，不会重复执行本方法
                        System.out.println("A2检测出铁开始，180s液位差在0.24~-0.26之间, " +
                                "A2castStartTime: " + A2castStartTime);//调试输出
                    }
                    //检测出铁结束，当180s液位差在-0.03~-0.01之间
                    else if ((-0.03 <= A2Speed) && (A2Speed <= -0.01) && !A2PourStartOrEnd) {
                        castTonnage = A2Weight;//读取出铁吨位
                        castEndTime = String.valueOf(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));//读取出铁结束时间
                        String sqlCast20 = "UPDATE product_blast_furnace_cast SET cast_end_time = NOW(), cast_total_time = (SELECT * FROM(SELECT TIMESTAMPDIFF(SECOND,cast_start_time,NOW()) FROM product_blast_furnace_cast WHERE equipment_location = ? ORDER BY blast_furnace_cast_id DESC LIMIT 1) as a), cast_tonnage = ? WHERE equipment_location = ? ORDER BY blast_furnace_cast_id DESC LIMIT 1";
                        runner.update(conn, sqlCast20, equipmentLocationA2, castTonnage, equipmentLocationA2);
                        A2PourStartOrEnd = true;//出铁开始/结束检测标志位取反，变成开始检测，不会重复执行本方法
                        System.out.println("A2检测出铁结束，180s液位差在-0.03~-0.01之间, " +
                                "castTonnage: " + castTonnage + ", A2Level: " + A2Level + ", castEndTime: " + castEndTime);//调试输出
                    }
                }
                //-------------------------------------------------A2核心逻辑--------------------------------------------

                //-------------------------------------------------A3核心逻辑--------------------------------------------
                //空包模块逻辑-----------------------------------------------液位低于下限且先前A3状态标志位是出铁或满包状态
                if ((A3Level <= A3statusLowerMark) && (A3StatusFlag != 0)) {
                    A3StatusFlag = 0;//A3状态标志位 0，此时进入空包状态，不会在空包状态下重复执行本方法
                    A3PourFlag = 1;//A3出铁检测标志位 1，进入空包状态后开启出铁检测
                    System.out.println("A3空包模块逻辑-----------------------------------------------液位低于下限且先前A3状态标志位是出铁或满包状态");//调试输出
                }
                //满包报警模块逻辑--------------------------------------------------液位在2.5~2.6之间，液位如果真的很高，那也是慢慢升高的，这个区间一定能检测到，但是瞬时3m高度是铁水包本身，不用检测，排除3m以上高度铁水包本身的干扰
                if ((2.5 <= A3Level) && (A3Level <= 2.6) && (A3StatusFlag != 2)) {
                    A3StatusFlag = 2;//A3状态标志位 2，此时进入满包报警状态，不会在满包报警状态下重复执行本方法
                    //A3PourFlag = 0;//A3出铁检测标志位 0，进入满包报警状态后关闭出铁检测
                    String sqlCast12 = "UPDATE product_blast_furnace_cast SET cast_alert_time = NOW() WHERE equipment_location = ? ORDER BY blast_furnace_cast_id DESC LIMIT 1";
                    runner.update(conn, sqlCast12, equipmentLocationA3);//更新出铁报警时间
                    System.out.println("A3满包报警模块逻辑--------------------------------------------------液位在2.5~2.6之间时执行, " +
                            "报警时当前液位为：: " + equipmentA3);//调试输出
                }
                //出铁模块逻辑-----------------------------------------------开启出铁检测，出铁标志位在空包时置1且当液位不高于2.7m时，高于2.7m自动忽略
                if ((A3PourFlag == 1) && (A3Level <= 2.7)) {
                    //读取历史液位数据，计算180s液位差
                    String sqlHistoryLevel = "SELECT equipment_A3 FROM product_blast_furnace_level ORDER BY blast_furnace_level_id DESC LIMIT 180,1";
                    ScalarHandler<Double> handerHistoryLevel =  new ScalarHandler<Double>("equipment_A3");
                    A3HistoryLevel = runner.query(conn,sqlHistoryLevel,handerHistoryLevel);
                    double A3Speed = A3Level - A3HistoryLevel;
                    System.out.println("A3出铁模块逻辑-----------------------------------------------开启出铁检测" + "180s液位差: " + A3Speed);
                    //检测出铁开始，当180s液位差在0.24~-0.26之间
                    if ((0.24 <= A3Speed) && (A3Speed <= 0.26) && A3PourStartOrEnd) {
                        A3StatusFlag = 1;//A3状态标志位 置出铁
                        A3castStartTime = System.currentTimeMillis();//计算出铁开始时间，给重量液位模块使用，数据库是自动装载的，与本方法无关
                        String sqlCast01 = "INSERT INTO product_blast_furnace_cast(equipment_location)VALUES(?)";
                        BeanHandler<Object> handerCast01 =  new BeanHandler<Object>(CollectionMain.class);
                        runner.insert(conn,sqlCast01,handerCast01,equipmentLocationA3);
                        A3PourStartOrEnd = false;//出铁开始/结束检测标志位取反，变成结束检测，不会重复执行本方法
                        System.out.println("A3检测出铁开始，180s液位差在0.24~-0.26之间, " +
                                "A3castStartTime: " + A3castStartTime);//调试输出
                    }
                    //检测出铁结束，当180s液位差在-0.03~-0.01之间
                    else if ((-0.03 <= A3Speed) && (A3Speed <= -0.01) && !A3PourStartOrEnd) {
                        castTonnage = A3Weight;//读取出铁吨位
                        castEndTime = String.valueOf(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));//读取出铁结束时间
                        String sqlCast20 = "UPDATE product_blast_furnace_cast SET cast_end_time = NOW(), cast_total_time = (SELECT * FROM(SELECT TIMESTAMPDIFF(SECOND,cast_start_time,NOW()) FROM product_blast_furnace_cast WHERE equipment_location = ? ORDER BY blast_furnace_cast_id DESC LIMIT 1) as a), cast_tonnage = ? WHERE equipment_location = ? ORDER BY blast_furnace_cast_id DESC LIMIT 1";
                        runner.update(conn, sqlCast20, equipmentLocationA3, castTonnage, equipmentLocationA3);
                        A3PourStartOrEnd = true;//出铁开始/结束检测标志位取反，变成开始检测，不会重复执行本方法
                        System.out.println("A3检测出铁结束，180s液位差在-0.03~-0.01之间, " +
                                "castTonnage: " + castTonnage + ", A3Level: " + A3Level + ", castEndTime: " + castEndTime);//调试输出
                    }
                }
                //-------------------------------------------------A3核心逻辑--------------------------------------------

                //-------------------------------------------------A6核心逻辑--------------------------------------------
                //空包模块逻辑-----------------------------------------------液位低于下限且先前A6状态标志位是出铁或满包状态
                if ((A6Level <= A6statusLowerMark) && (A6StatusFlag != 0)) {
                    A6StatusFlag = 0;//A6状态标志位 0，此时进入空包状态，不会在空包状态下重复执行本方法
                    A6PourFlag = 1;//A6出铁检测标志位 1，进入空包状态后开启出铁检测
                    System.out.println("A6空包模块逻辑-----------------------------------------------液位低于下限且先前A6状态标志位是出铁或满包状态");//调试输出
                }
                //满包报警模块逻辑--------------------------------------------------液位在2.5~2.6之间，液位如果真的很高，那也是慢慢升高的，这个区间一定能检测到，但是瞬时3m高度是铁水包本身，不用检测，排除3m以上高度铁水包本身的干扰
                if ((2.5 <= A6Level) && (A6Level <= 2.6) && (A6StatusFlag != 2)) {
                    A6StatusFlag = 2;//A6状态标志位 2，此时进入满包报警状态，不会在满包报警状态下重复执行本方法
                    //A6PourFlag = 0;//A6出铁检测标志位 0，进入满包报警状态后关闭出铁检测
                    String sqlCast12 = "UPDATE product_blast_furnace_cast SET cast_alert_time = NOW() WHERE equipment_location = ? ORDER BY blast_furnace_cast_id DESC LIMIT 1";
                    runner.update(conn, sqlCast12, equipmentLocationA6);//更新出铁报警时间
                    System.out.println("A6满包报警模块逻辑--------------------------------------------------液位在2.5~2.6之间时执行, " +
                            "报警时当前液位为：: " + equipmentA6);//调试输出
                }
                //出铁模块逻辑-----------------------------------------------开启出铁检测，出铁标志位在空包时置1且当液位不高于2.7m时，高于2.7m自动忽略
                if ((A6PourFlag == 1) && (A6Level <= 2.7)) {
                    //读取历史液位数据，计算180s液位差
                    String sqlHistoryLevel = "SELECT equipment_A6 FROM product_blast_furnace_level ORDER BY blast_furnace_level_id DESC LIMIT 180,1";
                    ScalarHandler<Double> handerHistoryLevel =  new ScalarHandler<Double>("equipment_A6");
                    A6HistoryLevel = runner.query(conn,sqlHistoryLevel,handerHistoryLevel);
                    double A6Speed = A6Level - A6HistoryLevel;
                    System.out.println("A6出铁模块逻辑-----------------------------------------------开启出铁检测" + "180s液位差: " + A6Speed);
                    //检测出铁开始，当180s液位差在0.24~-0.26之间
                    if ((0.24 <= A6Speed) && (A6Speed <= 0.26) && A6PourStartOrEnd) {
                        A6StatusFlag = 1;//A6状态标志位 置出铁
                        A6castStartTime = System.currentTimeMillis();//计算出铁开始时间，给重量液位模块使用，数据库是自动装载的，与本方法无关
                        String sqlCast01 = "INSERT INTO product_blast_furnace_cast(equipment_location)VALUES(?)";
                        BeanHandler<Object> handerCast01 =  new BeanHandler<Object>(CollectionMain.class);
                        runner.insert(conn,sqlCast01,handerCast01,equipmentLocationA6);
                        A6PourStartOrEnd = false;//出铁开始/结束检测标志位取反，变成结束检测，不会重复执行本方法
                        System.out.println("A6检测出铁开始，180s液位差在0.24~-0.26之间, " +
                                "A6castStartTime: " + A6castStartTime);//调试输出
                    }
                    //检测出铁结束，当180s液位差在-0.03~-0.01之间
                    else if ((-0.03 <= A6Speed) && (A6Speed <= -0.01) && !A6PourStartOrEnd) {
                        castTonnage = A6Weight;//读取出铁吨位
                        castEndTime = String.valueOf(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));//读取出铁结束时间
                        String sqlCast20 = "UPDATE product_blast_furnace_cast SET cast_end_time = NOW(), cast_total_time = (SELECT * FROM(SELECT TIMESTAMPDIFF(SECOND,cast_start_time,NOW()) FROM product_blast_furnace_cast WHERE equipment_location = ? ORDER BY blast_furnace_cast_id DESC LIMIT 1) as a), cast_tonnage = ? WHERE equipment_location = ? ORDER BY blast_furnace_cast_id DESC LIMIT 1";
                        runner.update(conn, sqlCast20, equipmentLocationA6, castTonnage, equipmentLocationA6);
                        A6PourStartOrEnd = true;//出铁开始/结束检测标志位取反，变成开始检测，不会重复执行本方法
                        System.out.println("A6检测出铁结束，180s液位差在-0.03~-0.01之间, " +
                                "castTonnage: " + castTonnage + ", A6Level: " + A6Level + ", castEndTime: " + castEndTime);//调试输出
                    }
                }
                //-------------------------------------------------A6核心逻辑--------------------------------------------

                //-------------------------------------------------A7核心逻辑--------------------------------------------
                //空包模块逻辑-----------------------------------------------液位低于下限且先前A7状态标志位是出铁或满包状态
                if ((A7Level <= A7statusLowerMark) && (A7StatusFlag != 0)) {
                    A7StatusFlag = 0;//A7状态标志位 0，此时进入空包状态，不会在空包状态下重复执行本方法
                    A7PourFlag = 1;//A7出铁检测标志位 1，进入空包状态后开启出铁检测
                    System.out.println("A7空包模块逻辑-----------------------------------------------液位低于下限且先前A7状态标志位是出铁或满包状态");//调试输出
                }
                //满包报警模块逻辑--------------------------------------------------液位在2.5~2.6之间，液位如果真的很高，那也是慢慢升高的，这个区间一定能检测到，但是瞬时3m高度是铁水包本身，不用检测，排除3m以上高度铁水包本身的干扰
                if ((2.5 <= A7Level) && (A7Level <= 2.6) && (A7StatusFlag != 2)) {
                    A7StatusFlag = 2;//A7状态标志位 2，此时进入满包报警状态，不会在满包报警状态下重复执行本方法
                    //A7PourFlag = 0;//A7出铁检测标志位 0，进入满包报警状态后关闭出铁检测
                    String sqlCast12 = "UPDATE product_blast_furnace_cast SET cast_alert_time = NOW() WHERE equipment_location = ? ORDER BY blast_furnace_cast_id DESC LIMIT 1";
                    runner.update(conn, sqlCast12, equipmentLocationA7);//更新出铁报警时间
                    System.out.println("A7满包报警模块逻辑--------------------------------------------------液位在2.5~2.6之间时执行, " +
                            "报警时当前液位为：: " + equipmentA7);//调试输出
                }
                //出铁模块逻辑-----------------------------------------------开启出铁检测，出铁标志位在空包时置1且当液位不高于2.7m时，高于2.7m自动忽略
                if ((A7PourFlag == 1) && (A7Level <= 2.7)) {
                    //读取历史液位数据，计算180s液位差
                    String sqlHistoryLevel = "SELECT equipment_A7 FROM product_blast_furnace_level ORDER BY blast_furnace_level_id DESC LIMIT 180,1";
                    ScalarHandler<Double> handerHistoryLevel =  new ScalarHandler<Double>("equipment_A7");
                    A7HistoryLevel = runner.query(conn,sqlHistoryLevel,handerHistoryLevel);
                    double A7Speed = A7Level - A7HistoryLevel;
                    System.out.println("A7出铁模块逻辑-----------------------------------------------开启出铁检测" + "180s液位差: " + A7Speed);
                    //检测出铁开始，当180s液位差在0.24~-0.26之间
                    if ((0.24 <= A7Speed) && (A7Speed <= 0.26) && A7PourStartOrEnd) {
                        A7StatusFlag = 1;//A7状态标志位 置出铁
                        A7castStartTime = System.currentTimeMillis();//计算出铁开始时间，给重量液位模块使用，数据库是自动装载的，与本方法无关
                        String sqlCast01 = "INSERT INTO product_blast_furnace_cast(equipment_location)VALUES(?)";
                        BeanHandler<Object> handerCast01 =  new BeanHandler<Object>(CollectionMain.class);
                        runner.insert(conn,sqlCast01,handerCast01,equipmentLocationA7);
                        A7PourStartOrEnd = false;//出铁开始/结束检测标志位取反，变成结束检测，不会重复执行本方法
                        System.out.println("A7检测出铁开始，180s液位差在0.24~-0.26之间, " +
                                "A7castStartTime: " + A7castStartTime);//调试输出
                    }
                    //检测出铁结束，当180s液位差在-0.03~-0.01之间
                    else if ((-0.03 <= A7Speed) && (A7Speed <= -0.01) && !A7PourStartOrEnd) {
                        castTonnage = A7Weight;//读取出铁吨位
                        castEndTime = String.valueOf(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));//读取出铁结束时间
                        String sqlCast20 = "UPDATE product_blast_furnace_cast SET cast_end_time = NOW(), cast_total_time = (SELECT * FROM(SELECT TIMESTAMPDIFF(SECOND,cast_start_time,NOW()) FROM product_blast_furnace_cast WHERE equipment_location = ? ORDER BY blast_furnace_cast_id DESC LIMIT 1) as a), cast_tonnage = ? WHERE equipment_location = ? ORDER BY blast_furnace_cast_id DESC LIMIT 1";
                        runner.update(conn, sqlCast20, equipmentLocationA7, castTonnage, equipmentLocationA7);
                        A7PourStartOrEnd = true;//出铁开始/结束检测标志位取反，变成开始检测，不会重复执行本方法
                        System.out.println("A7检测出铁结束，180s液位差在-0.03~-0.01之间, " +
                                "castTonnage: " + castTonnage + ", A7Level: " + A7Level + ", castEndTime: " + castEndTime);//调试输出
                    }
                }
                //-------------------------------------------------A7核心逻辑--------------------------------------------

                //-------------------------------------------------A8核心逻辑--------------------------------------------
                //空包模块逻辑-----------------------------------------------液位低于下限且先前A8状态标志位是出铁或满包状态
                if ((A8Level <= A8statusLowerMark) && (A8StatusFlag != 0)) {
                    A8StatusFlag = 0;//A8状态标志位 0，此时进入空包状态，不会在空包状态下重复执行本方法
                    A8PourFlag = 1;//A8出铁检测标志位 1，进入空包状态后开启出铁检测
                    System.out.println("A8空包模块逻辑-----------------------------------------------液位低于下限且先前A8状态标志位是出铁或满包状态");//调试输出
                }
                //A8满包报警模块逻辑--------------------------------------------------液位在2.5~2.6之间，液位如果真的很高，那也是慢慢升高的，这个区间一定能检测到，但是瞬时3m高度是铁水包本身，不用检测，排除3m以上高度铁水包本身的干扰
                if ((2.5 <= A8Level) && (A8Level <= 2.6) && (A8StatusFlag != 2)) {
                    A8StatusFlag = 2;//A8状态标志位 2，此时进入满包报警状态，不会在满包报警状态下重复执行本方法
                    //A8PourFlag = 0;//A8出铁检测标志位 0，进入满包报警状态后关闭出铁检测
                    String sqlCast12 = "UPDATE product_blast_furnace_cast SET cast_alert_time = NOW() WHERE equipment_location = ? ORDER BY blast_furnace_cast_id DESC LIMIT 1";
                    runner.update(conn, sqlCast12, equipmentLocationA8);//更新出铁报警时间
                    System.out.println("A8满包报警模块逻辑--------------------------------------------------液位在2.5~2.6之间时执行, " +
                            "报警时当前液位为：: " + equipmentA8);//调试输出
                }
                //A8出铁模块逻辑-----------------------------------------------开启出铁检测，出铁标志位在空包时置1且当液位不高于2.7m时，高于2.7m自动忽略
                if ((A8PourFlag == 1) && (A8Level <= 2.7)) {
                    //读取历史液位数据，计算180s液位差
                    String sqlHistoryLevel = "SELECT equipment_A8 FROM product_blast_furnace_level ORDER BY blast_furnace_level_id DESC LIMIT 180,1";
                    ScalarHandler<Double> handerHistoryLevel =  new ScalarHandler<Double>("equipment_A8");
                    A8HistoryLevel = runner.query(conn,sqlHistoryLevel,handerHistoryLevel);
                    double A8Speed = A8Level - A8HistoryLevel;
                    System.out.println("A8出铁模块逻辑-----------------------------------------------开启出铁检测" + "180s液位差: " + A8Speed);
                    //检测出铁开始，当180s液位差在0.24~-0.26之间
                    if ((0.24 <= A8Speed) && (A8Speed <= 0.26) && A8PourStartOrEnd) {
                        A8StatusFlag = 1;//A8状态标志位 置出铁
                        A8castStartTime = System.currentTimeMillis();//计算出铁开始时间，给重量液位模块使用，数据库是自动装载的，与本方法无关
                        String sqlCast01 = "INSERT INTO product_blast_furnace_cast(equipment_location)VALUES(?)";
                        BeanHandler<Object> handerCast01 =  new BeanHandler<Object>(CollectionMain.class);
                        runner.insert(conn,sqlCast01,handerCast01,equipmentLocationA8);
                        A8PourStartOrEnd = false;//出铁开始/结束检测标志位取反，变成结束检测，不会重复执行本方法
                        System.out.println("A8检测出铁开始，180s液位差在0.24~-0.26之间, " +
                                "A8castStartTime: " + A8castStartTime);//调试输出
                    }
                    //检测出铁结束，当180s液位差在-0.03~-0.01之间
                    else if ((-0.03 <= A8Speed) && (A8Speed <= -0.01) && !A8PourStartOrEnd) {
                        castTonnage = A8Weight;//读取出铁吨位
                        castEndTime = String.valueOf(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));//读取出铁结束时间
                        String sqlCast20 = "UPDATE product_blast_furnace_cast SET cast_end_time = NOW(), cast_total_time = (SELECT * FROM(SELECT TIMESTAMPDIFF(SECOND,cast_start_time,NOW()) FROM product_blast_furnace_cast WHERE equipment_location = ? ORDER BY blast_furnace_cast_id DESC LIMIT 1) as a), cast_tonnage = ? WHERE equipment_location = ? ORDER BY blast_furnace_cast_id DESC LIMIT 1";
                        runner.update(conn, sqlCast20, equipmentLocationA8, castTonnage, equipmentLocationA8);
                        A8PourStartOrEnd = true;//出铁开始/结束检测标志位取反，变成开始检测，不会重复执行本方法
                        System.out.println("A8检测出铁结束，180s液位差在-0.03~-0.01之间, " +
                                "castTonnage: " + castTonnage + ", A8Level: " + A8Level + ", castEndTime: " + castEndTime);//调试输出
                    }
                }
                //-------------------------------------------------A8核心逻辑--------------------------------------------

                //输出当前液位数据给液位表
                String sqlLevel = "INSERT INTO product_blast_furnace_level" +
                        "(equipment_a1,equipment_a2,equipment_a3,equipment_a6,equipment_a7,equipment_a8)VALUES(?,?,?,?,?,?)";
                BeanHandler<Object> handerLevel =  new BeanHandler<Object>(CollectionMain.class);
                runner.insert(conn,sqlLevel,handerLevel,A1Level,A2Level,A3Level,A6Level,A7Level,A8Level);

                //输出当前状态数据给状态表
                String sqlStatus = "INSERT INTO product_blast_furnace_status" +
                        "(equipment_a1_state,equipment_a2_state,equipment_a3_state,equipment_a6_state,equipment_a7_state,equipment_a8_state)VALUES(?,?,?,?,?,?)";
                BeanHandler<Object> handerStatus =  new BeanHandler<Object>(CollectionMain.class);
                runner.insert(conn,sqlStatus,handerStatus,A1StatusFlag,A2StatusFlag,A3StatusFlag,A6StatusFlag,A7StatusFlag,A8StatusFlag);

                //计算当前重量数据
                if (equipmentA1 <= 0.93) {
                    A1Weight = String.format("%.3f", Math.PI * 10.374 * Math.pow(equipmentA1, 2) - Math.PI * 2.6 * Math.pow(equipmentA1, 3));//分段函数第一段
                } else if (equipmentA1 > 1.43) {
                    A1Weight = String.format("%.3f", Math.PI * 15.766 * equipmentA1 - Math.PI * 7.705);//分段函数第三段
                } else {
                    A1Weight = String.format("%.3f", Math.PI * 13.797 * equipmentA1 - Math.PI * 5.95);//分段函数第二段
                }
                if (equipmentA2 <= 0.93) {
                    A2Weight = String.format("%.3f", Math.PI * 10.374 * Math.pow(equipmentA2, 2) - Math.PI * 2.6 * Math.pow(equipmentA2, 3));//分段函数第一段
                } else if (equipmentA1 > 1.43) {
                    A2Weight = String.format("%.3f", Math.PI * 15.766 * equipmentA2 - Math.PI * 7.705);//分段函数第三段
                } else {
                    A2Weight = String.format("%.3f", Math.PI * 13.797 * equipmentA2 - Math.PI * 5.95);//分段函数第二段
                }
                if (equipmentA3 <= 0.93) {
                    A3Weight = String.format("%.3f", Math.PI * 10.374 * Math.pow(equipmentA3, 2) - Math.PI * 2.6 * Math.pow(equipmentA3, 3));//分段函数第一段
                } else if (equipmentA1 > 1.43) {
                    A3Weight = String.format("%.3f", Math.PI * 15.766 * equipmentA3 - Math.PI * 7.705);//分段函数第三段
                } else {
                    A3Weight = String.format("%.3f", Math.PI * 13.797 * equipmentA3 - Math.PI * 5.95);//分段函数第二段
                }
                if (equipmentA6 <= 0.93) {
                    A6Weight = String.format("%.3f", Math.PI * 10.374 * Math.pow(equipmentA6, 2) - Math.PI * 2.6 * Math.pow(equipmentA6, 3));//分段函数第一段
                } else if (equipmentA1 > 1.43) {
                    A6Weight = String.format("%.3f", Math.PI * 15.766 * equipmentA6 - Math.PI * 7.705);//分段函数第三段
                } else {
                    A6Weight = String.format("%.3f", Math.PI * 13.797 * equipmentA6 - Math.PI * 5.95);//分段函数第二段
                }
                if (equipmentA7 <= 0.93) {
                    A7Weight = String.format("%.3f", Math.PI * 10.374 * Math.pow(equipmentA7, 2) - Math.PI * 2.6 * Math.pow(equipmentA7, 3));//分段函数第一段
                } else if (equipmentA1 > 1.43) {
                    A7Weight = String.format("%.3f", Math.PI * 15.766 * equipmentA7 - Math.PI * 7.705);//分段函数第三段
                } else {
                    A7Weight = String.format("%.3f", Math.PI * 13.797 * equipmentA7 - Math.PI * 5.95);//分段函数第二段
                }
                if (equipmentA8 <= 0.93) {
                    A8Weight = String.format("%.3f", Math.PI * 10.374 * Math.pow(equipmentA8, 2) - Math.PI * 2.6 * Math.pow(equipmentA8, 3));//分段函数第一段
                } else if (equipmentA1 > 1.43) {
                    A8Weight = String.format("%.3f", Math.PI * 15.766 * equipmentA8 - Math.PI * 7.705);//分段函数第三段
                } else {
                    A8Weight = String.format("%.3f", Math.PI * 13.797 * equipmentA8 - Math.PI * 5.95);//分段函数第二段
                }
                //System.out.println("A1Weight: " + A1Weight + ", A2Weight: " + A2Weight + ", A3Weight: " + A3Weight + ", A6Weight: " + A6Weight + ", A7Weight: " + A7Weight + ", A8Weight: " + A8Weight);
                //计算当前流量
                long currentTime = System.currentTimeMillis();
                double A1castTotalTime = (double) (currentTime - A1castStartTime);
                double A2castTotalTime = (double) (currentTime - A2castStartTime);
                double A3castTotalTime = (double) (currentTime - A3castStartTime);
                double A6castTotalTime = (double) (currentTime - A6castStartTime);
                double A7castTotalTime = (double) (currentTime - A7castStartTime);
                double A8castTotalTime = (double) (currentTime - A8castStartTime);
                A1Flow = String.format("%.3f", 60000 * Double.parseDouble(A1Weight)/A1castTotalTime);//分母是毫秒单位，需要放大成分钟单位
                A2Flow = String.format("%.3f", 60000 * Double.parseDouble(A2Weight)/A2castTotalTime);//分母是毫秒单位，需要放大成分钟单位
                A3Flow = String.format("%.3f", 60000 * Double.parseDouble(A3Weight)/A3castTotalTime);//分母是毫秒单位，需要放大成分钟单位
                A6Flow = String.format("%.3f", 60000 * Double.parseDouble(A6Weight)/A6castTotalTime);//分母是毫秒单位，需要放大成分钟单位
                A7Flow = String.format("%.3f", 60000 * Double.parseDouble(A7Weight)/A7castTotalTime);//分母是毫秒单位，需要放大成分钟单位
                A8Flow = String.format("%.3f", 60000 * Double.parseDouble(A8Weight)/A8castTotalTime);//分母是毫秒单位，需要放大成分钟单位
                //输出当前重量数据给重量表
                String sqlWeight = "UPDATE product_blast_furnace_weight SET blast_furnace_level_id = (SELECT * FROM(SELECT blast_furnace_level_id FROM product_blast_furnace_level ORDER BY blast_furnace_level_id DESC LIMIT 1) as a), equipment_a1_weight = ?, equipment_a2_weight = ?, equipment_a3_weight = ?, equipment_a6_weight = ?, equipment_a7_weight = ?, equipment_a8_weight = ?";
                runner.update(conn,sqlWeight,A1Weight,A2Weight,A3Weight,A6Weight,A7Weight,A8Weight);
                //输出当前流量数据给流量流速表
                String sqlSpeedAndFlow = "UPDATE product_blast_furnace_speed SET blast_furnace_level_id_index = (SELECT * FROM(SELECT blast_furnace_level_id FROM product_blast_furnace_level ORDER BY blast_furnace_level_id DESC LIMIT 1) as a), " +
                        "equipment_a1_flow = ?, equipment_a2_flow = ?, equipment_a3_flow = ?, equipment_a6_flow = ?, equipment_a7_flow = ?, equipment_a8_flow = ? WHERE blast_furnace_speed_id = 1";
                runner.update(conn,sqlSpeedAndFlow,A1Flow,A2Flow,A3Flow,A6Flow,A7Flow,A8Flow);

                //时间测试结束
                //System.out.println(System.currentTimeMillis() - testTime);

                Thread.sleep(740);//确保整个程序循环频率为1Hz
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                masterCom3.destroy();
                masterCom4.destroy();
                //关闭数据库连接
                //conn.close();
            }
        }
    }

    /**
     * 读保持寄存器上的内容
     *
     * @param master  主站
     * @param slaveId 从站地址
     */
    private static short[] readHoldingRegistersTest(ModbusMaster master, int slaveId) {
        try {
            ReadHoldingRegistersRequest request = new ReadHoldingRegistersRequest(slaveId, 8, 4);
            ReadHoldingRegistersResponse response = (ReadHoldingRegistersResponse)master.send(request);
            if (response.isException()) {
                System.out.println("Exception response: message=" + response.getExceptionMessage());
            } else {
                //System.out.println(Arrays.toString(response.getShortData()));
                return response.getShortData();
            }
        } catch (ModbusTransportException e) {
            e.printStackTrace();
        }
        return null;
    }
}
