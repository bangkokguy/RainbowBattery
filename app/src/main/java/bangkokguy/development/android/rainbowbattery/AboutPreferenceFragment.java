package bangkokguy.development.android.rainbowbattery;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceFragment;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * A simple {@link Fragment} subclass.
 * Activities that contain this fragment must implement the
 * {@link AboutPreferenceFragment.OnFragmentInteractionListener} interface
 * to handle interaction events.
 */
public class AboutPreferenceFragment extends PreferenceFragment {

    private OnFragmentInteractionListener mListener;

    public AboutPreferenceFragment() {
        // Required empty public constructor
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        LinearLayout v = (LinearLayout) inflater.inflate(R.layout.custom_preference, container, false);
        TextView t1 = (TextView)v.findViewById(R.id.build_type);
        TextView t2 = (TextView)v.findViewById(R.id.version_name);
        TextView t3 = (TextView)v.findViewById(R.id.version_code);

        String s1 = BuildConfig.BUILD_TYPE;
        String s2 = BuildConfig.VERSION_NAME;
        String s3 = Integer.toString(BuildConfig.VERSION_CODE);

        t1.setText(s1);
        t2.setText(s2);
        t3.setText(s3);


/*        LinearLayout v = new LinearLayout(getActivity());

        ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);

        TextView t1 = new TextView(getActivity());
        TextView t2 = new TextView(getActivity());
        TextView t3 = new TextView(getActivity());

        t1.setText(R.string.hello_blank_fragment+"-"+s1);
        t2.setText(R.string.hello_blank_fragment+"-"+s2);
        t3.setText(R.string.hello_blank_fragment+"-"+s3);
        t1.setLayoutParams(params);
        t2.setLayoutParams(params);
        t3.setLayoutParams(params);

        v.addView(t1);
        v.addView(t2);
        v.addView(t3);*/

        return v;
        //textView;
    }

    // TODO: Rename method, update argument and hook method into UI event
    public void onButtonPressed(Uri uri) {
        if (mListener != null) {
            mListener.onFragmentInteraction(uri);
        }
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof OnFragmentInteractionListener) {
            mListener = (OnFragmentInteractionListener) context;
        } else {
            throw new RuntimeException(context.toString()
                    + " must implement OnFragmentInteractionListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    /**
     * This interface must be implemented by activities that contain this
     * fragment to allow an interaction in this fragment to be communicated
     * to the activity and potentially other fragments contained in that
     * activity.
     * <p>
     * See the Android Training lesson <a href=
     * "http://developer.android.com/training/basics/fragments/communicating.html"
     * >Communicating with Other Fragments</a> for more information.
     */
    public interface OnFragmentInteractionListener {
        // TODO: Update argument type and name
        void onFragmentInteraction(Uri uri);
    }
}
