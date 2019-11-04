package jp.panta.misskeyandroidclient.view.account

import android.app.Dialog
import android.content.Intent
import android.util.Log
import android.view.View
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import jp.panta.misskeyandroidclient.AuthActivity
import jp.panta.misskeyandroidclient.MiApplication
import jp.panta.misskeyandroidclient.R
import kotlinx.android.synthetic.main.dialog_switch_account.view.*

class AccountSwitchingDialog : BottomSheetDialogFragment(){

    override fun setupDialog(dialog: Dialog, style: Int) {
        super.setupDialog(dialog, style)

        val view = View.inflate(context, R.layout.dialog_switch_account,null)
        dialog.setContentView(view)

        val miApplication = context?.applicationContext as MiApplication
        //miApplication.connectionInstanceDao?.findAll()
        val accounts = miApplication.connectionInstancesLiveData.value
        if(accounts == null){
            Log.w("AccountSwitchDialog", "アカウント達の取得に失敗しました")
            Toast.makeText(this.context, "アカウントの取得に失敗しました", Toast.LENGTH_LONG).show()
            dismiss()
            return
        }
        val activity = activity
        if(activity == null){
            dismiss()
            return
        }

        view.add_account.setOnClickListener {
            startActivity(Intent(activity, AuthActivity::class.java))
            dismiss()
        }


    }
}