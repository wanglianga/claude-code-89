<template>
  <div>
    <h2 class="page-title">低收入家庭定向领取</h2>
    <div class="page-sub">社区核验登记需求 → 分拣中心按尺码/季节/卫生/属地匹配已签收捐赠批 → 领取身份与代领核验签收 → 异常在同一回收单协同 → 回访复盘 · 公示端不展示任何家庭明细</div>

    <el-tabs v-model="tab">
      <!-- 1. 需求登记 -->
      <el-tab-pane name="families">
        <el-card>
          <div class="toolbar">
            <el-tag type="info">所有姓名/电话均脱敏登记；困难情况仅业务端可见</el-tag>
            <div style="flex:1"></div>
            <el-button v-if="canRegister" type="primary" :icon="Plus" @click="openFamily">登记救助需求</el-button>
            <el-button :icon="Refresh" @click="loadFamilies" />
          </div>
          <el-table :data="families" size="small" stripe>
            <el-table-column prop="maskedName" label="脱敏姓名" width="90" />
            <el-table-column label="小区/街道" width="170">
              <template #default="{row}">{{ row.communityName }} / {{ row.subdistrict || '-' }}</template>
            </el-table-column>
            <el-table-column label="资格核验" width="170">
              <template #default="{row}">
                <el-tag size="small">{{ AID_PROOF[row.proofType] }}</el-tag>
                <div class="muted">{{ row.proofNote }}</div>
              </template>
            </el-table-column>
            <el-table-column label="需求" min-width="220">
              <template #default="{row}">
                {{ row.needCount }}人 · {{ row.genderAgeDesc }} · 尺码{{ row.sizes }} · {{ row.seasons }}
                <div class="muted">{{ row.clothTypes }} {{ row.needShoesBagsBedding ? '· 需鞋包被褥' : '' }}
                  {{ row.acceptUsed ? '' : '· 仅接受新衣' }} · {{ row.deliveryMethod==='DELIVERY'?'配送':'自提' }}</div>
                <el-tag v-if="row.designatedTarget" size="small" type="warning">指定对象：{{ row.designatedTarget }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="voucherNo" label="凭证号" width="130" />
            <el-table-column label="状态" width="110">
              <template #default="{row}"><el-tag size="small" :type="AID_FAMILY_STATUS[row.status].type">{{ AID_FAMILY_STATUS[row.status].label }}</el-tag></template>
            </el-table-column>
            <el-table-column label="操作" width="230" fixed="right">
              <template #default="{row}">
                <el-button v-if="canMatch && row.status!=='RECEIVED'" link type="primary" size="small" @click="openMatch(row)">分拣匹配</el-button>
                <el-button link type="info" size="small" @click="openVisit(row)">回访</el-button>
                <el-button link type="warning" size="small" @click="viewFamily(row)">详情</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <!-- 2. 领取任务 -->
      <el-tab-pane name="dist">
        <el-card>
          <el-table :data="distributions" size="small" stripe>
            <el-table-column prop="voucherNo" label="凭证号" width="125" />
            <el-table-column label="家庭" width="90">
              <template #default="{row}">{{ row.familyPublicHidden ? '匿名家庭' : row.familyName }}</template>
            </el-table-column>
            <el-table-column label="批次/项目" min-width="190">
              <template #default="{row}">{{ row.batch.code }} · {{ row.batch.projectName }}</template>
            </el-table-column>
            <el-table-column label="计划/实领" width="100">
              <template #default="{row}">{{ row.plannedQuantity }} / {{ row.actualQuantity || '-' }}</template>
            </el-table-column>
            <el-table-column label="领取核验" min-width="180">
              <template #default="{row}">
                <template v-if="row.status==='HANDED_OUT' || row.status==='EXCHANGED'">
                  {{ row.receiverRelation }} · {{ row.handedBy }}
                  <el-tag v-if="row.proxyName" size="small" type="warning">代领：{{ row.proxyName }}</el-tag>
                  <el-image v-if="row.signPhotoUrl" :src="row.signPhotoUrl" style="width:42px;height:30px;border-radius:4px"
                            :preview-src-list="[row.signPhotoUrl]" fit="cover" />
                </template>
                <span v-else class="muted">待领取核验</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{row}"><el-tag size="small" :type="AID_DIST_STATUS[row.status].type">{{ AID_DIST_STATUS[row.status].label }}</el-tag></template>
            </el-table-column>
            <el-table-column label="操作" width="210" fixed="right">
              <template #default="{row}">
                <el-button v-if="canHandout && row.status==='MATCHED'" link type="success" size="small" @click="openHandout(row)">领取核验</el-button>
                <el-button v-if="canIssue && ['MATCHED','HANDED_OUT'].includes(row.status)" link type="danger" size="small" @click="openIssue(row)">报异常</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <!-- 3. 异常协同 -->
      <el-tab-pane name="issues">
        <el-row :gutter="14">
          <el-col :md="9">
            <el-card>
              <el-scrollbar height="62vh">
                <div v-for="i in issues" :key="i.id" class="issue-item" :class="{active: currentIssue?.id===i.id}" @click="currentIssue=i">
                  <el-tag size="small" type="danger">{{ i.typeLabel }}</el-tag>
                  <el-tag size="small" :style="{marginLeft:'6px'}">{{ i.orderCode }}</el-tag>
                  <div class="muted" style="margin-top:4px">{{ i.description }}</div>
                </div>
                <el-empty v-if="!issues.length" :image-size="70" description="暂无异常" />
              </el-scrollbar>
            </el-card>
          </el-col>
          <el-col :md="15">
            <el-card v-if="currentIssue">
              <template #header>
                <b>异常 #{{ currentIssue.id }} · {{ currentIssue.typeLabel }} · 回收单 {{ currentIssue.orderCode }}</b>
                <el-tag size="small" style="margin-left:8px">{{ currentIssue.status }}</el-tag>
              </template>
              <el-timeline>
                <el-timeline-item v-for="e in currentIssue.events" :key="e.id" :timestamp="fmt(e.createdAt)">
                  <el-tag size="small">{{ e.partyRole }}</el-tag> <b>{{ e.authorName }}</b>：{{ e.content }}
                </el-timeline-item>
              </el-timeline>
              <el-input v-model="issueReply" type="textarea" :rows="2" placeholder="各角色在同一回收单中协同回复" />
              <div style="margin-top:10px;display:flex;gap:8px">
                <el-button type="primary" plain @click="replyIssue">补充进展</el-button>
                <div style="flex:1"></div>
                <el-select v-model="resolveAction" placeholder="处置方式" style="width:170px" v-if="canResolve">
                  <el-option label="换货" value="EXCHANGE" />
                  <el-option label="退回重新分拣" value="RETURN_RESORT" />
                  <el-option label="转其他登记家庭" value="TRANSFER_FAMILY" />
                  <el-option label="补充分拣" value="ADDITIONAL_SORT" />
                  <el-option label="取消（临时放弃）" value="CANCEL" />
                </el-select>
                <el-button v-if="canResolve" type="warning" @click="resolveIssue">处置结案</el-button>
              </div>
            </el-card>
          </el-col>
        </el-row>
      </el-tab-pane>

      <!-- 4. 公益价值记账 -->
      <el-tab-pane name="value">
        <el-alert type="warning" :closable="false" class="section-card"
                  title="同一发放批次的物资公益价值只记一次；居民已选择积分的来源单不再重复计算捐赠金额，避免积分/捐赠金额/物资价值重复入账" />
        <el-card>
          <div class="toolbar">
            <span>已记账批次：{{ valueRecords.length }}</span>
            <div style="flex:1"></div>
            <el-button v-if="canValue" type="primary" @click="openValue">登记公益价值</el-button>
          </div>
          <el-table :data="valueRecords" size="small">
            <el-table-column prop="batchCode" label="批次" width="130" />
            <el-table-column prop="projectName" label="公益项目" min-width="180" />
            <el-table-column prop="quantity" label="发放件数" width="100" />
            <el-table-column label="公益价值" width="120">
              <template #default="{row}">￥{{ row.valueAmount }}</template>
            </el-table-column>
            <el-table-column prop="recordedBy" label="记账人" width="100" />
            <el-table-column prop="remark" label="备注" min-width="160" />
          </el-table>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <!-- 登记家庭 -->
    <el-dialog v-model="familyDlg" title="登记救助需求（社区核验）" width="680px" top="4vh">
      <el-form :model="ff" label-width="120px">
        <el-row :gutter="10">
          <el-col :span="12"><el-form-item label="脱敏姓名" required><el-input v-model="ff.maskedName" placeholder="如 张*" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="脱敏电话"><el-input v-model="ff.maskedPhone" placeholder="138****2103" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="小区"><el-input v-model="ff.communityName" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="街道"><el-input v-model="ff.subdistrict" placeholder="用于属地优先调剂" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="资格证明" required>
            <el-select v-model="ff.proofType" style="width:100%">
              <el-option v-for="(l,k) in AID_PROOF" :key="k" :label="l" :value="k" />
            </el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="核验结论"><el-input v-model="ff.proofNote" placeholder="材料核验摘要，不留证件影像" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="家庭人数"><el-input-number v-model="ff.familySize" :min="1" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="需求人数"><el-input-number v-model="ff.needCount" :min="1" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="领取方式">
            <el-radio-group v-model="ff.deliveryMethod"><el-radio value="PICKUP">自提</el-radio><el-radio value="DELIVERY">配送</el-radio></el-radio-group>
          </el-form-item></el-col>
          <el-col :span="24"><el-form-item label="性别年龄"><el-input v-model="ff.genderAgeDesc" placeholder="如 女35岁、男8岁、女6岁" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="所需尺码"><el-input v-model="ff.sizes" placeholder="130,S,M" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="适用季节"><el-input v-model="ff.seasons" placeholder="秋冬" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="衣物类型"><el-input v-model="ff.clothTypes" placeholder="羽绒服、秋衣、童鞋…" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="接受二手"><el-switch v-model="ff.acceptUsed" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="需鞋包被褥"><el-switch v-model="ff.needShoesBagsBedding" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="企业/学校指定"><el-input v-model="ff.designatedTarget" placeholder="无指定留空" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="公示隐藏明细"><el-switch v-model="ff.publicHidden" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="困难情况"><el-input v-model="ff.needNote" type="textarea" :rows="2" placeholder="仅业务端可见，不对外公示" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer><el-button @click="familyDlg=false">取消</el-button><el-button type="primary" @click="submitFamily">核验登记</el-button></template>
    </el-dialog>

    <!-- 分拣匹配 -->
    <el-dialog v-model="matchDlg" title="分拣中心匹配（仅可直接捐赠·已签收批）" width="780px" top="4vh">
      <el-alert type="info" :closable="false" class="section-card"
                :title="`${matchFamily?.maskedName} 需求：${matchFamily?.needCount}人 · 尺码 ${matchFamily?.sizes} · ${matchFamily?.seasons} · ${matchFamily?.clothTypes}`" />
      <el-radio-group v-model="matchBatchId" @change="loadCandidateOrders" style="margin-bottom:10px">
        <el-radio-button v-for="c in candidates" :key="c.id" :value="c.id" :label="c.id">
          {{ c.code }} {{ c.projectName }}
          <el-tag v-if="c.localPriority" size="small" type="success">属地优先</el-tag>
          <el-tag v-if="c.designatedTarget" size="small" type="warning">指定：{{ c.designatedTarget }}</el-tag>
        </el-radio-button>
      </el-radio-group>
      <el-table :data="candidateOrders" size="small" max-height="300"
                @selection-change="rows => matchOrders = rows" @select-all="()=>{}">
        <el-table-column type="selection" width="44" />
        <el-table-column prop="code" label="单号" width="110" />
        <el-table-column prop="communityName" label="小区" width="120" />
        <el-table-column label="分类"><template #default="{row}"><el-tag size="small" type="success">可直接捐赠</el-tag></template></el-table-column>
        <el-table-column label="重量/卫生" min-width="140">
          <template #default="{row}">{{ row.sort.weightKg }}kg · 分拣员 {{ row.sort.sorter.displayName }}</template>
        </el-table-column>
      </el-table>
      <el-form label-width="100px" style="margin-top:10px">
        <el-form-item label="计划发放件数"><el-input-number v-model="matchQty" :min="1" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="matchDlg=false">取消</el-button><el-button type="primary" @click="submitMatch">生成定向领取任务</el-button></template>
    </el-dialog>

    <!-- 领取核验 -->
    <el-dialog v-model="handoutDlg" title="领取身份核验与签收" width="560px">
      <el-form :model="hf" label-width="110px">
        <el-form-item label="与登记人关系" required>
          <el-select v-model="hf.receiverRelation" style="width:100%">
            <el-option label="本人" value="本人" />
            <el-option label="配偶" value="配偶" />
            <el-option label="子女" value="子女" />
            <el-option label="其他亲属" value="其他亲属" />
            <el-option label="社区工作人员代领" value="社区工作人员代领" />
          </el-select>
        </el-form-item>
        <template v-if="hf.receiverRelation && hf.receiverRelation!=='本人'">
          <el-form-item label="代领人姓名" required><el-input v-model="hf.proxyName" placeholder="脱敏姓名" /></el-form-item>
          <el-form-item label="代领授权" required>
            <el-input v-model="hf.proxyAuthNote" type="textarea" :rows="2" placeholder="登记人电话确认 / 社区确认授权书编号（无授权不得发放）" />
          </el-form-item>
        </template>
        <el-form-item label="实际领取件数" required><el-input-number v-model="hf.actualQuantity" :min="1" /></el-form-item>
        <el-form-item label="签收照片" required>
          <input type="file" accept="image/*" @change="onSignFile" />
          <el-image v-if="hf.signUrl" :src="hf.signUrl" style="width:160px;margin-top:6px" />
          <div class="muted">或在备注中留存本人签字说明</div>
        </el-form-item>
        <el-form-item label="签字/备注"><el-input v-model="hf.handNote" type="textarea" :rows="2" placeholder="无照片时填写签字情况" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="handoutDlg=false">取消</el-button><el-button type="success" @click="submitHandout">核验通过并签收</el-button></template>
    </el-dialog>

    <!-- 报异常 -->
    <el-dialog v-model="issueDlg" title="上报领取异常（关联同一回收单）" width="520px">
      <el-form label-width="100px">
        <el-form-item label="异常类型">
          <el-select v-model="issueForm.type" style="width:100%">
            <el-option v-for="(l,k) in AID_ISSUE_TYPE" :key="k" :label="l" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item label="关联回收单">
          <el-select v-model="issueForm.orderId" style="width:100%" placeholder="选择具体问题回收单">
            <el-option v-for="id in (currentDist?.matchedOrderIds||'').split(',').filter(Boolean)" :key="id" :label="'#'+id" :value="Number(id)" />
          </el-select>
        </el-form-item>
        <el-form-item label="情况说明"><el-input v-model="issueForm.description" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="issueDlg=false">取消</el-button><el-button type="danger" @click="submitIssue">上报并进入协同</el-button></template>
    </el-dialog>

    <!-- 回访 -->
    <el-dialog v-model="visitDlg" title="社区回访" width="520px">
      <el-form label-width="100px">
        <el-form-item label="穿着情况"><el-input v-model="vf.wearingSituation" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="满意度"><el-rate v-model="vf.satisfaction" /></el-form-item>
        <el-form-item label="后续需求"><el-input v-model="vf.followupNeed" type="textarea" :rows="2" placeholder="结果进入项目账本与小区复盘" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="visitDlg=false">取消</el-button><el-button type="primary" @click="submitVisit">提交回访</el-button></template>
    </el-dialog>

    <!-- 公益价值 -->
    <el-dialog v-model="valueDlg" title="登记批次公益价值（每批一次）" width="520px">
      <el-form label-width="100px">
        <el-form-item label="已发放批次">
          <el-select v-model="valueForm.batchId" style="width:100%">
            <el-option v-for="d in distributions.filter(x=>x.status==='HANDED_OUT'||x.status==='EXCHANGED')" :key="d.batch.id"
                       :label="d.batch.code + ' · ' + d.batch.projectName" :value="d.batch.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="价值金额(元)"><el-input-number v-model="valueForm.valueAmount" :min="0" :precision="2" /></el-form-item>
        <el-form-item label="件数"><el-input-number v-model="valueForm.quantity" :min="1" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="valueForm.remark" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="valueDlg=false">取消</el-button><el-button type="primary" @click="submitValue">记账</el-button></template>
    </el-dialog>

    <!-- 家庭详情 -->
    <el-drawer v-model="familyDetailDlg" size="480px" :title="matchFamily?.maskedName + ' 详情'">
      <template v-if="detailFamily">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="凭证">{{ detailFamily.voucherNo }}</el-descriptions-item>
          <el-descriptions-item label="资格">{{ AID_PROOF[detailFamily.proofType] }}：{{ detailFamily.proofNote }}</el-descriptions-item>
          <el-descriptions-item label="需求">{{ detailFamily.genderAgeDesc }}；尺码{{ detailFamily.sizes }}；{{ detailFamily.seasons }}；{{ detailFamily.clothTypes }}</el-descriptions-item>
        </el-descriptions>
        <el-divider>领取记录</el-divider>
        <el-timeline>
          <el-timeline-item v-for="d in detailFamily.distributions" :key="d.id" :timestamp="fmt(d.createdAt)">
            {{ d.batch.code }} · {{ AID_DIST_STATUS[d.status].label }}
            <span v-if="d.actualQuantity">· 实领 {{ d.actualQuantity }} 件</span>
          </el-timeline-item>
        </el-timeline>
        <el-divider>回访</el-divider>
        <div v-for="v in familyVisits" :key="v.id" class="visit-line">
          <el-rate :model-value="v.satisfaction" disabled size="small" /> {{ v.wearingSituation }}
          <div class="muted" v-if="v.followupNeed">后续需求：{{ v.followupNeed }}</div>
        </div>
        <el-empty v-if="!familyVisits.length" :image-size="60" description="暂无回访" />
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import api from '../api'
import { useAuth } from '../store'
import { AID_PROOF, AID_FAMILY_STATUS, AID_DIST_STATUS, AID_ISSUE_TYPE } from '../aidMeta'

const auth = useAuth()
const role = computed(() => auth.role)
const canRegister = computed(() => ['COMMUNITY', 'ADMIN'].includes(role.value))
const canMatch = computed(() => ['SORTER', 'ADMIN'].includes(role.value))
const canHandout = computed(() => ['COMMUNITY', 'ORG', 'ADMIN'].includes(role.value))
const canIssue = computed(() => ['RESIDENT', 'COLLECTOR', 'SORTER', 'COMMUNITY', 'ORG', 'ADMIN'].includes(role.value))
const canResolve = computed(() => ['SORTER', 'COMMUNITY', 'ORG', 'ADMIN'].includes(role.value))
const canValue = computed(() => ['FINANCE', 'ADMIN'].includes(role.value))

const tab = ref(canMatch.value ? 'families' : 'families')
const families = ref([])
const distributions = ref([])
const issues = ref([])
const valueRecords = ref([])

async function loadFamilies() { families.value = await api.get('/api/aid/families') }
async function loadDist() { distributions.value = await api.get('/api/aid/distributions') }
async function loadIssues() { issues.value = await api.get('/api/aid/issues') }
async function loadValues() { valueRecords.value = await api.get('/api/aid/value') }
onMounted(async () => {
  try { await loadFamilies() } catch (e) {}
  try { distributions.value = await api.get('/api/aid/distributions') } catch (e) {}
  try { issues.value = await api.get('/api/aid/issues') } catch (e) {}
  try { valueRecords.value = await api.get('/api/aid/value') } catch (e) {}
})

function fmt(t) { return t ? t.replace('T', ' ').slice(0, 16) : '' }

// 登记
const familyDlg = ref(false)
const ff = ref({})
function openFamily() {
  ff.value = { proofType: 'LOW_INCOME', familySize: 1, needCount: 1, acceptUsed: true,
    deliveryMethod: 'PICKUP', publicHidden: true, communityName: auth.user.communityName || '' }
  familyDlg.value = true
}
async function submitFamily() {
  if (!ff.value.maskedName || !ff.value.proofType) return ElMessage.warning('姓名与资格证明必填')
  await api.post('/api/aid/families', ff.value)
  ElMessage.success('已核验登记并生成凭证'); familyDlg.value = false; loadFamilies()
}

// 匹配
const matchDlg = ref(false)
const matchFamily = ref(null)
const candidates = ref([])
const candidateOrders = ref([])
const matchBatchId = ref(null)
const matchOrders = ref([])
const matchQty = ref(3)
async function openMatch(row) {
  matchFamily.value = row
  candidates.value = await api.get(`/api/aid/candidates?familyId=${row.id}`)
  if (!candidates.value.length) return ElMessage.warning('暂无可直接捐赠的已签收批次（待消毒/再生/不可回收均不可发放）')
  matchBatchId.value = candidates.value[0].id; matchOrders.value = []
  await loadCandidateOrders()
  matchDlg.value = true
}
async function loadCandidateOrders() {
  const b = candidates.value.find(c => c.id === matchBatchId.value)
  const detail = await api.get(`/api/batches/${matchBatchId.value}`)
  candidateOrders.value = detail.orders.filter(o =>
    !o.anonymous && o.category === 'DIRECT_DONATE' && o.status === 'DONATED')
  matchQty.value = (matchFamily.value.needCount || 1) * 3
}
async function submitMatch() {
  if (!matchOrders.value.length) return ElMessage.warning('请勾选匹配回收单')
  await api.post('/api/aid/match', {
    familyId: matchFamily.value.id, batchId: matchBatchId.value,
    orderIds: matchOrders.value.map(o => o.id), plannedQuantity: matchQty.value })
  ElMessage.success('已生成定向领取任务，通知社区经办人'); matchDlg.value = false
  loadFamilies(); loadDist()
}

// 领取核验
const handoutDlg = ref(false)
const hf = ref({})
let currentDist = null
function openHandout(row) {
  currentDist = row
  hf.value = { receiverRelation: '本人', actualQuantity: row.plannedQuantity, proxyName: '', proxyAuthNote: '', handNote: '', signUrl: '', _file: null }
  handoutDlg.value = true
}
function onSignFile(e) { hf.value._file = e.target.files[0]; hf.value.signUrl = URL.createObjectURL(e.target.files[0]) }
async function submitHandout() {
  if (!hf.value.receiverRelation) return ElMessage.warning('请核验关系')
  if (hf.value.receiverRelation !== '本人' && (!hf.value.proxyName || !hf.value.proxyAuthNote))
    return ElMessage.warning('代领必须登记代领人与授权依据')
  if (!hf.value._file && !hf.value.handNote) return ElMessage.warning('需签收照片或签字记录')
  const fd = new FormData()
  Object.entries(hf.value).forEach(([k, v]) => { if (k[0] !== '_' && k !== 'signUrl' && v !== null && v !== '') fd.append(k, String(v)) })
  if (hf.value._file) fd.append('signPhoto', hf.value._file)
  await api.post(`/api/aid/distributions/${currentDist.id}/handout`, fd)
  ElMessage.success('领取完成'); handoutDlg.value = false; loadDist(); loadFamilies(); loadValues()
}

// 异常
const issueDlg = ref(false)
const issueForm = ref({})
function openIssue(row) {
  currentDist = row
  issueForm.value = { type: 'SIZE_WRONG', orderId: null, description: '' }
  issueDlg.value = true
}
async function submitIssue() {
  if (!issueForm.value.orderId) return ElMessage.warning('请选择关联回收单')
  if (!issueForm.value.description) return ElMessage.warning('请填写情况说明')
  await api.post('/api/aid/issues', { distributionId: currentDist.id, ...issueForm.value })
  ElMessage.success('异常已上报，各方在同一回收单协同处理'); issueDlg.value = false; loadIssues()
}
const currentIssue = ref(null)
const issueReply = ref('')
const resolveAction = ref('')
async function replyIssue() {
  if (!issueReply.value) return
  await api.post(`/api/aid/issues/${currentIssue.value.id}/events`, { content: issueReply.value })
  issueReply.value = ''; loadIssues()
}
async function resolveIssue() {
  if (!resolveAction.value) return ElMessage.warning('请选择处置方式')
  await api.post(`/api/aid/issues/${currentIssue.value.id}/resolve`, { action: resolveAction.value, note: '按规范处置，未直接修改公示去向' })
  ElMessage.success('已处置（换货/退回/转家庭/补充分拣，公示保持原始留痕）'); loadIssues(); loadDist(); loadFamilies()
}

// 回访
const visitDlg = ref(false)
const vf = ref({})
const detailFamily = ref(null)
const familyDetailDlg = ref(false)
const familyVisits = ref([])
let visitFamily = null
function openVisit(row) {
  visitFamily = row
  vf.value = { satisfaction: 5, wearingSituation: '', followupNeed: '' }
  visitDlg.value = true
}
async function submitVisit() {
  await api.post(`/api/aid/families/${visitFamily.id}/visit`, vf.value)
  ElMessage.success('回访已记录，进入项目账本与小区复盘'); visitDlg.value = false
}
async function viewFamily(row) {
  visitFamily = row
  detailFamily.value = await api.get(`/api/aid/families/${row.id}`)
  familyVisits.value = await api.get(`/api/aid/families/${row.id}/visits`)
  familyDetailDlg.value = true
}

// 价值记账
const valueDlg = ref(false)
const valueForm = ref({})
function openValue() { valueForm.value = { batchId: null, valueAmount: 100, quantity: 10, remark: '' }; valueDlg.value = true }
async function submitValue() {
  if (!valueForm.value.batchId) return ElMessage.warning('请选择批次')
  try {
    await api.post('/api/aid/value', valueForm.value)
    ElMessage.success('公益价值已记账（该批次仅记一次）'); valueDlg.value = false; loadValues()
  } catch (e) { /* 拦截器提示 */ }
}
</script>

<style scoped>
.toolbar { display:flex; gap:10px; align-items:center; margin-bottom:12px; }
.muted { color:#97a0af; font-size:12px; }
.issue-item { border:1px solid #edf0f2; border-radius:8px; padding:10px; margin-bottom:8px; cursor:pointer; }
.issue-item.active { border-color:var(--brand); background:var(--brand-light); }
.visit-line { font-size:13px; padding:6px 0; border-bottom:1px dashed #eef0f2; }
</style>
